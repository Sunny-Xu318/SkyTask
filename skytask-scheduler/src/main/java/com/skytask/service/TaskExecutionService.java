package com.skytask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytask.client.WorkerClient;
import com.skytask.config.SchedulerDynamicProperties;
import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import com.skytask.entity.TaskEntity;
import com.skytask.entity.TaskInstanceEntity;
import com.skytask.entity.TaskRetryEntity;
import com.skytask.exception.ResourceNotFoundException;
import com.skytask.executor.TaskExecutor;
import com.skytask.executor.TaskExecutorFactory;
import com.skytask.model.ExecutorType;
import com.skytask.model.RetryPolicy;
import com.skytask.model.TaskExecutionRecord;
import com.skytask.model.TaskStatus;
import com.skytask.repository.TaskInstanceRepository;
import com.skytask.repository.TaskParamRepository;
import com.skytask.repository.TaskRepository;
import com.skytask.repository.TaskRetryRepository;
import com.skytask.scheduler.TaskSchedulerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_BACKOFF_MS = 60_000;

    private final TaskRepository taskRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskParamRepository taskParamRepository;
    private final TaskRetryRepository taskRetryRepository;
    private final RedissonClient redissonClient;
    @Qualifier("taskExecutionExecutor")
    private final Executor taskExecutionExecutor;
    private final TaskSchedulerService taskSchedulerService;
    private final WorkerClient workerClient;
    private final SchedulerDynamicProperties schedulerDynamicProperties;
    private final TenantResolver tenantResolver;
    private final MeterRegistry meterRegistry;
    private final FailureMonitor failureMonitor;
    private final TaskExecutorFactory taskExecutorFactory;

    private Counter executionSuccessCounter;
    private Counter executionFailureCounter;
    private Timer schedulingDelayTimer;
    private Timer workerDispatchTimer;

    @PostConstruct
    void initMetrics() {
        this.executionSuccessCounter = Counter.builder("skytask_task_executions_total")
                .tag("result", "success")
                .description("Total number of successful task executions")
                .register(meterRegistry);
        this.executionFailureCounter = Counter.builder("skytask_task_executions_total")
                .tag("result", "failure")
                .description("Total number of failed task executions")
                .register(meterRegistry);
        this.schedulingDelayTimer = Timer.builder("skytask_task_scheduling_delay")
                .description("Delay between scheduled fire time and actual execution start")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        this.workerDispatchTimer = Timer.builder("skytask_worker_dispatch_duration")
                .description("Time taken to dispatch execution request to worker")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void executeScheduled(String taskId, Date scheduledTime) {
        executeScheduled(taskId, scheduledTime, 0);
    }

    public void executeScheduled(String taskId, Date scheduledTime, int attempt) {
        executeInternal(taskId, false, "SCHEDULER", null, scheduledTime, attempt);
    }

    public TaskExecutionRecord executeManual(String taskId, String operator, String payload) {
        return executeInternal(taskId, true, operator, payload, new Date(), 0);
    }

    private TaskExecutionRecord executeInternal(
            String taskId, boolean manual, String operator, String payload, Date scheduledTime, int attempt) {
        Long tenantId = tenantResolver.currentTenantId();
        TaskEntity task = taskRepository
                .findByTenantIdAndId(tenantId, Long.valueOf(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        Map<String, String> paramMap = loadParamMap(tenantId, task.getId());
        int timeoutSeconds = parseInteger(
                paramMap.get("timeout"), schedulerDynamicProperties.getRetry().getTimeoutSeconds());
        RetryPolicy retryPolicy = resolveRetryPolicy(
                paramMap.get("retryPolicy"), schedulerDynamicProperties.getRetry().getDefaultPolicy());
        int maxRetry = task.getRetryMax() != null
                ? task.getRetryMax()
                : schedulerDynamicProperties.getRetry().getMaxAttempts();
        int baseBackoffMs = task.getRetryBackoffMs() != null
                ? task.getRetryBackoffMs()
                : schedulerDynamicProperties.getRetry().getBaseBackoffMs();

        if (!manual && scheduledTime != null && schedulingDelayTimer != null) {
            long delayMillis = Math.max(0, Instant.now().toEpochMilli() - scheduledTime.getTime());
            if (delayMillis > 0) {
                schedulingDelayTimer.record(delayMillis, TimeUnit.MILLISECONDS);
            }
        }

        String lockKey = "skytask:tenant:" + tenantId + ":task:" + taskId + ":lock";
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Skip execution for task {} because lock could not be acquired", taskId);
                return null;
            }
            String triggerSource = manual ? (StringUtils.hasText(operator) ? operator : "MANUAL") : "SCHEDULER";
            TaskInstanceEntity instance = new TaskInstanceEntity();
            instance.setTenantId(tenantId);
            instance.setTaskId(task.getId());
            instance.setInstanceId(UUID.randomUUID().toString());
            instance.setScheduledTime(toLocalDateTime(scheduledTime));
            instance.setTriggeredAt(LocalDateTime.now());
            instance.setTriggeredBy(triggerSource);
            instance.setStatus("RUNNING");
            instance.setAttempts(attempt);
            instance.setTimeoutMs(timeoutSeconds);
            instance.setResult(payload);
            instance.setCreatedAt(Instant.now());
            TaskInstanceEntity saved = taskInstanceRepository.save(instance);

            Map<String, String> dispatchParams = Map.copyOf(paramMap);
            String tenantCode = tenantResolver.resolveTenantCode(tenantId);
            Timer.Sample dispatchSample = Timer.start(meterRegistry);
            taskExecutionExecutor.execute(() -> dispatchToWorker(
                    tenantId,
                    tenantCode,
                    task,
                    saved,
                    dispatchParams,
                    triggerSource,
                    timeoutSeconds,
                    retryPolicy,
                    maxRetry,
                    baseBackoffMs,
                    attempt,
                    dispatchSample));
            return toRecord(saved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring task lock", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void dispatchToWorker(
            Long tenantId,
            String tenantCode,
            TaskEntity task,
            TaskInstanceEntity instance,
            Map<String, String> paramMap,
            String operator,
            int timeoutSeconds,
            RetryPolicy retryPolicy,
            int maxRetry,
            int baseBackoffMs,
            int attempt,
            Timer.Sample dispatchSample) {
        Long resolvedTenantId = tenantId != null ? tenantId : task.getTenantId();
        String resolvedTenantCode = StringUtils.hasText(tenantCode)
                ? tenantCode
                : tenantResolver.resolveTenantCode(resolvedTenantId);
        try {
            WorkerExecuteRequest request = WorkerExecuteRequest.builder()
                    .taskId(task.getId().toString())
                    .instanceId(instance.getInstanceId())
                    .tenantCode(resolvedTenantCode)
                    .operator(operator)
                    .parameters(toWorkerParameters(paramMap))
                    .timeoutSeconds(timeoutSeconds)
                    .attempt(attempt)
                    .build();
            
            // 获取执行器类型和 Handler
            ExecutorType executorType = resolveExecutorType(paramMap.get("executorType"));
            String handler = task.getHandler();
            
            WorkerExecutionResult response;
            
            // 根据执行器类型选择执行方式
            if (executorType != null && executorType != ExecutorType.GRPC && 
                StringUtils.hasText(handler)) {
                // 使用新的执行器系统
                log.info("Dispatching task {} using {} executor to handler: {}", 
                    task.getId(), executorType, handler);
                
                try {
                    TaskExecutor executor = taskExecutorFactory.getExecutor(executorType, handler);
                    response = executor.execute(handler, request, resolvedTenantCode);
                } catch (Exception e) {
                    log.error("Executor failed for task {}: {}", task.getId(), e.getMessage(), e);
                    response = WorkerExecutionResult.builder()
                        .taskId(String.valueOf(task.getId()))
                        .instanceId(instance.getInstanceId())
                        .status("FAILED")
                        .message("Executor error: " + e.getMessage())
                        .attempt(attempt)
                        .build();
                }
            } else {
                // 回退到原有的 Worker 方式（GRPC 或未配置 handler）
                log.info("Dispatching task {} using legacy Worker client", task.getId());
                response = workerClient.executeTask(String.valueOf(task.getId()), request, resolvedTenantCode);
            }
            
            log.info(
                    "Dispatch task {} instance {} attempt {} -> execution result: {}",
                    task.getId(),
                    instance.getInstanceId(),
                    attempt,
                    response != null ? response.getStatus() : "NULL");
                    
            // 更新任务实例状态
            if (response != null && StringUtils.hasText(response.getStatus())) {
                String resultStatus = response.getStatus().toUpperCase(Locale.ROOT);
                instance.setStatus(resultStatus);
                instance.setResult(response.getMessage());
                instance.setFinishedAt(Instant.now());
                taskInstanceRepository.save(instance);
                
                log.info("Task instance {} updated with status: {}", instance.getInstanceId(), resultStatus);
                
                // 记录指标
                boolean success = "SUCCESS".equals(resultStatus);
                recordOutcome(task, resolvedTenantId, success);
                
                // 如果失败，安排重试
                if (!success) {
                    scheduleRetryIfNeeded(task, instance, resolvedTenantId, resolvedTenantCode, 
                        retryPolicy, maxRetry, baseBackoffMs, attempt);
                }
            } else {
                // 如果响应为空或状态为空，标记为未知
                log.warn("Task {} execution returned null or empty status, marking as FAILED", task.getId());
                instance.setStatus("FAILED");
                instance.setResult("Execution returned null or invalid response");
                instance.setFinishedAt(Instant.now());
                taskInstanceRepository.save(instance);
                recordOutcome(task, resolvedTenantId, false);
            }
        } catch (Exception ex) {
            log.error(
                    "Failed to dispatch task {} instance {} to worker: {}",
                    task.getId(),
                    instance.getInstanceId(),
                    ex.getMessage(),
                    ex);
            handleDispatchFailure(
                    resolvedTenantId, resolvedTenantCode, task, instance, retryPolicy, maxRetry, baseBackoffMs, attempt, ex.getMessage());
        } finally {
            if (dispatchSample != null && workerDispatchTimer != null) {
                dispatchSample.stop(workerDispatchTimer);
            }
        }
    }
    
    /**
     * 解析执行器类型
     */
    private ExecutorType resolveExecutorType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ExecutorType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid executor type: {}", value);
            return null;
        }
    }

    private void handleDispatchFailure(
            Long tenantId,
            String tenantCode,
            TaskEntity task,
            TaskInstanceEntity instance,
            RetryPolicy retryPolicy,
            int maxRetry,
            int baseBackoffMs,
            int attempt,
            String reason) {
        instance.setFinishedAt(Instant.now());
        instance.setStatus("FAILED");
        instance.setResult("Dispatch worker failed: " + (StringUtils.hasText(reason) ? reason : "unknown"));
        instance.setAttempts(attempt);
        taskInstanceRepository.save(instance);

        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        recordOutcome(task, tenantId, false);

        scheduleRetryIfNeeded(task, instance, tenantId, tenantCode, retryPolicy, maxRetry, baseBackoffMs, attempt);
    }

    @Transactional
    public void handleWorkerResult(WorkerExecutionResult result) {
        Long tenantId = tenantResolver.currentTenantId();
        Long taskId = Long.valueOf(result.getTaskId());
        TaskEntity task = taskRepository
                .findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + result.getTaskId()));
        TaskInstanceEntity instance = taskInstanceRepository
                .findByTenantIdAndInstanceId(tenantId, result.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Task instance not found: " + result.getInstanceId()));

        instance.setFinishedAt(Instant.now());
        instance.setStatus(result.getStatus());
        instance.setResult(result.getMessage());
        instance.setAttempts(result.getAttempt());
        taskInstanceRepository.save(instance);

        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        boolean success = "SUCCESS".equalsIgnoreCase(result.getStatus());
        recordOutcome(task, tenantId, success);

        Map<String, String> paramMap = loadParamMap(tenantId, task.getId());
        RetryPolicy retryPolicy = resolveRetryPolicy(
                paramMap.get("retryPolicy"), schedulerDynamicProperties.getRetry().getDefaultPolicy());
        int maxRetry = task.getRetryMax() != null
                ? task.getRetryMax()
                : schedulerDynamicProperties.getRetry().getMaxAttempts();
        int baseBackoffMs = task.getRetryBackoffMs() != null
                ? task.getRetryBackoffMs()
                : schedulerDynamicProperties.getRetry().getBaseBackoffMs();

        if (!success) {
            String tenantCode = tenantResolver.resolveTenantCode(tenantId);
            scheduleRetryIfNeeded(
                    task, instance, tenantId, tenantCode, retryPolicy, maxRetry, baseBackoffMs, result.getAttempt());
        }
    }

    private void scheduleRetryIfNeeded(
            TaskEntity task,
            TaskInstanceEntity instance,
            Long tenantId,
            String tenantCode,
            RetryPolicy retryPolicy,
            int maxRetry,
            int baseBackoffMs,
            int attempt) {
        if (retryPolicy == RetryPolicy.NONE) {
            log.info("Retry policy NONE for task {}, no retry will be scheduled", task.getId());
            return;
        }
        int nextAttempt = attempt + 1;
        if (nextAttempt > maxRetry) {
            log.info("Task {} reached max retry attempts ({})", task.getId(), maxRetry);
            return;
        }
        long delayMs = computeDelay(baseBackoffMs, retryPolicy, nextAttempt);
        if (delayMs <= 0) {
            log.info("Computed retry delay zero for task {}, skipping retry", task.getId());
            return;
        }

        LocalDateTime nextTime = LocalDateTime.now().plusSeconds(delayMs / 1000);
        instance.setStatus("RETRY_SCHEDULED");
        taskInstanceRepository.save(instance);

        TaskRetryEntity retry = new TaskRetryEntity();
        retry.setTenantId(tenantId != null ? tenantId : task.getTenantId());
        retry.setInstanceId(instance.getInstanceId());
        retry.setRetryNo(nextAttempt);
        retry.setScheduledRetryTime(nextTime);
        retry.setStatus("SCHEDULED");
        retry.setCreatedAt(Instant.now());
        taskRetryRepository.save(retry);

        Date fireTime = Date.from(nextTime.atZone(ZoneId.systemDefault()).toInstant());
        String resolvedTenantCode = StringUtils.hasText(tenantCode)
                ? tenantCode
                : tenantResolver.resolveTenantCode(task.getTenantId());
        Long resolvedTenantId = tenantId != null ? tenantId : task.getTenantId();
        taskSchedulerService.scheduleRetry(String.valueOf(task.getId()), resolvedTenantId, resolvedTenantCode, fireTime, nextAttempt);
    }

    private void recordOutcome(TaskEntity task, Long tenantId, boolean success) {
        if (task == null) {
            return;
        }
        if (success) {
            if (executionSuccessCounter != null) {
                executionSuccessCounter.increment();
            }
        } else {
            if (executionFailureCounter != null) {
                executionFailureCounter.increment();
            }
        }
        failureMonitor.recordResult(task.getId(), tenantId, task.getName(), success);
    }

    private long computeDelay(int baseBackoffMs, RetryPolicy policy, int attempt) {
        int safeBase = baseBackoffMs > 0 ? baseBackoffMs : DEFAULT_BACKOFF_MS;
        switch (policy) {
            case FIXED_INTERVAL:
                return safeBase;
            case EXP_BACKOFF:
                long multiplier = 1L << Math.max(0, attempt - 1);
                return Math.min(safeBase * multiplier, TimeUnit.MINUTES.toMillis(30));
            default:
                return -1;
        }
    }

    private Map<String, String> loadParamMap(Long tenantId, Long taskId) {
        List<com.skytask.entity.TaskParamEntity> params = taskParamRepository.findByTenantIdAndTaskId(tenantId, taskId);
        return params.stream()
                .collect(Collectors.toMap(com.skytask.entity.TaskParamEntity::getParamKey,
                        com.skytask.entity.TaskParamEntity::getParamValue,
                        (a, b) -> b));
    }

    private Map<String, Object> toWorkerParameters(Map<String, String> paramMap) {
        Map<String, Object> parameters = new HashMap<>();
        if (paramMap != null) {
            paramMap.forEach(parameters::put);
        }
        return parameters;
    }

    private RetryPolicy resolveRetryPolicy(String value, String defaultValue) {
        String fallback = StringUtils.hasText(defaultValue) ? defaultValue : RetryPolicy.EXP_BACKOFF.name();
        if (!StringUtils.hasText(value)) {
            return safeRetryPolicy(fallback);
        }
        try {
            return RetryPolicy.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return safeRetryPolicy(fallback);
        }
    }

    private RetryPolicy safeRetryPolicy(String value) {
        try {
            return RetryPolicy.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return RetryPolicy.EXP_BACKOFF;
        }
    }

    private int parseInteger(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private TaskExecutionRecord toRecord(TaskInstanceEntity entity) {
        return TaskExecutionRecord.builder()
                .id(String.valueOf(entity.getId()))
                .taskId(String.valueOf(entity.getTaskId()))
                .triggerTime(toOffsetDateTime(entity.getTriggeredAt(), entity.getScheduledTime()))
                .node(null)
                .status(mapStatus(entity.getStatus()))
                .duration(0)
                .retry(entity.getAttempts() != null ? entity.getAttempts() : 0)
                .log(entity.getResult())
                .traceId(entity.getInstanceId())
                .parameters(parseResultParameters(entity.getResult()))
                .build();
    }

    private Map<String, Object> parseResultParameters(String result) {
        if (!StringUtils.hasText(result)) {
            return new HashMap<>();
        }
        try {
            // 尝试解析 JSON 格式的结果
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> resultMap = mapper.readValue(result, Map.class);
            
            // 提取参数信息
            Map<String, Object> parameters = new HashMap<>();
            if (resultMap.containsKey("parameters")) {
                Object params = resultMap.get("parameters");
                if (params instanceof Map) {
                    parameters.putAll((Map<String, Object>) params);
                }
            }
            
            // 如果结果本身就是参数格式，直接使用
            if (parameters.isEmpty() && resultMap.size() > 0) {
                parameters.putAll(resultMap);
            }
            
            return parameters;
        } catch (Exception e) {
            // 如果解析失败，返回空参数
            return new HashMap<>();
        }
    }

    private TaskStatus mapStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return TaskStatus.UNKNOWN;
        }
        switch (status.toUpperCase(Locale.ROOT)) {
            case "SUCCESS":
                return TaskStatus.SUCCESS;
            case "FAILED":
                return TaskStatus.FAILED;
            case "RUNNING":
                return TaskStatus.RUNNING;
            case "TIMEOUT":
                return TaskStatus.DEGRADED;
            case "SCHEDULED":
            case "PENDING":
            case "RETRY_SCHEDULED":
                return TaskStatus.SCHEDULED;
            default:
                return TaskStatus.UNKNOWN;
        }
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime triggeredAt, LocalDateTime scheduledTime) {
        LocalDateTime reference = triggeredAt != null ? triggeredAt : scheduledTime;
        if (reference == null) {
            return null;
        }
        return reference.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
