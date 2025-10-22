package com.skytask.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytask.dto.PageResponse;
import com.skytask.dto.TaskMetricsResponse;
import com.skytask.dto.TaskRequest;
import com.skytask.dto.TaskTriggerRequest;
import com.skytask.context.RequestContextAdapter;
import com.skytask.context.TenantContextHolder;
import com.skytask.entity.TaskDependencyEntity;
import com.skytask.entity.TaskEntity;
import com.skytask.entity.TaskInstanceEntity;
import com.skytask.entity.TaskParamEntity;
import com.skytask.exception.ResourceNotFoundException;
import com.skytask.model.ExecutorType;
import com.skytask.model.RetryPolicy;
import com.skytask.model.RouteStrategy;
import com.skytask.model.TaskExecutionRecord;
import com.skytask.model.TaskDependency;
import com.skytask.model.TaskInfo;
import com.skytask.model.TaskStatus;
import com.skytask.model.TaskType;
import com.skytask.repository.TaskInstanceRepository;
import com.skytask.repository.TaskParamRepository;
import com.skytask.repository.TaskRepository;
import com.skytask.repository.TaskDependencyRepository;
import com.skytask.scheduler.TaskSchedulerService;
import com.skytask.service.dto.TaskQuery;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String PARAM_OWNER = "owner";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_ROUTE_STRATEGY = "routeStrategy";
    private static final String PARAM_RETRY_POLICY = "retryPolicy";
    private static final String PARAM_IDEMPOTENT_KEY = "idempotentKey";
    private static final String PARAM_PARAMETERS = "parameters";
    private static final String PARAM_ALERT_ENABLED = "alertEnabled";
    private static final String PARAM_TIMEOUT = "timeout";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    private final TaskRepository taskRepository;
    private final TaskParamRepository taskParamRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final ObjectMapper objectMapper;
    private final TaskSchedulerService taskSchedulerService;
    private final TaskExecutionService taskExecutionService;
    private final TenantResolver tenantResolver;
    private final RequestContextAdapter requestContextAdapter;
    private final TaskDependencyRepository taskDependencyRepository;

    @PostConstruct
    public void initializeSchedulers() {
        taskRepository.findAll().forEach(entity -> {
            String tenantCode = tenantResolver.resolveTenantCode(entity.getTenantId());
            try {
                TenantContextHolder.setTenantCode(tenantCode);
                TaskInfo info = toTaskInfo(entity, loadParamMap(entity.getTenantId(), entity.getId()));
                taskSchedulerService.scheduleTask(info);
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    @Transactional(readOnly = true)
    public TaskMetricsResponse loadMetrics(String range) {
        Long tenantId = tenantResolver.currentTenantId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = "7d".equalsIgnoreCase(range) ? now.minusDays(7) : now.minusHours(24);

        long total = taskRepository.countByTenantId(tenantId);
        long inactive = taskRepository.countByTenantIdAndEnabled(tenantId, Boolean.FALSE);
        long successCount = taskInstanceRepository.countByTenantIdAndStatus(tenantId, "SUCCESS");
        long failedCount = taskInstanceRepository.countByTenantIdAndStatus(tenantId, "FAILED");
        double successRate = (successCount + failedCount) == 0
                ? 100.0
                : (successCount * 100.0) / (successCount + failedCount);
        long failedToday = taskInstanceRepository.countByTenantIdAndStatusAndScheduledTimeAfter(
                tenantId, "FAILED", now.minusDays(1));
        long backlog = taskInstanceRepository.countByTenantIdAndStatus(tenantId, "PENDING");

        List<Map<String, Object>> trend = buildTrendData(tenantId, rangeStart);
        List<Map<String, Object>> events = buildRecentEvents(tenantId);
        List<Map<String, Object>> topFailed = buildTopFailedTasks(tenantId);

        return TaskMetricsResponse.builder()
                .totalTasks(total)
                .inactiveTasks(inactive)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .failedToday(failedToday)
                .backlog(backlog)
                .trend(trend)
                .recentEvents(events)
                .topFailed(topFailed)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskInfo> listTasks(TaskQuery query) {
        Long tenantId = tenantResolver.currentTenantId();
        List<TaskEntity> entities = taskRepository.findAllByTenantId(tenantId);
        entities.sort(Comparator.comparing(TaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<TaskInfo> infos = entities.stream()
                .map(task -> toTaskInfo(task, loadParamMap(tenantId, task.getId())))
                .collect(Collectors.toList());

        List<TaskInfo> filtered = infos.stream()
                .filter(info -> filterByKeyword(info, query.getKeyword()))
                .filter(info -> filterByStatus(info, query.getStatus()))
                .filter(info -> filterByOwner(info, query.getOwner()))
                .filter(info -> filterByTags(info, query.getTags()))
                .collect(Collectors.toList());

        int page = Math.max(query.getPage(), 1);
        int size = Math.max(query.getSize(), 10);
        int fromIndex = Math.min((page - 1) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<TaskInfo> pageRecords =
                fromIndex >= filtered.size() ? Collections.emptyList() : filtered.subList(fromIndex, toIndex);

        return PageResponse.<TaskInfo>builder()
                .records(pageRecords)
                .total(filtered.size())
                .page(page)
                .size(size)
                .build();
    }

    @Transactional(readOnly = true)
    public TaskInfo getTask(String taskId) {
        Long id = parseId(taskId);
        Long tenantId = tenantResolver.currentTenantId();
        TaskEntity entity = taskRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        return toTaskInfo(entity, loadParamMap(tenantId, entity.getId()));
    }

    @Transactional
    public TaskInfo createTask(TaskRequest request) {
        Long tenantId = tenantResolver.currentTenantId();
        if (taskRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new IllegalArgumentException("Task name already exists: " + request.getName());
        }
        validateTaskRequest(request);
        TaskEntity entity = new TaskEntity();
        applyTaskRequest(entity, request);
        entity.setEnabled(true);
        entity.setTenantId(tenantId);
        String operator = requestContextAdapter.currentUser();
        entity.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        TaskEntity saved = taskRepository.save(entity);

        persistParams(tenantId, saved.getId(), request);

        TaskInfo info = toTaskInfo(saved, loadParamMap(tenantId, saved.getId()));
        taskSchedulerService.scheduleTask(info);
        return info;
    }

    @Transactional
    public TaskInfo updateTask(String taskId, TaskRequest request) {
        Long id = parseId(taskId);
        Long tenantId = tenantResolver.currentTenantId();
        TaskEntity entity = taskRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        validateTaskRequest(request);
        applyTaskRequest(entity, request);
        entity.setUpdatedAt(Instant.now());
        TaskEntity saved = taskRepository.save(entity);

        taskParamRepository.deleteByTenantIdAndTaskId(tenantId, id);
        persistParams(tenantId, id, request);

        TaskInfo info = toTaskInfo(saved, loadParamMap(tenantId, id));
        taskSchedulerService.scheduleTask(info);
        return info;
    }

    @Transactional
    public void deleteTask(String taskId) {
        Long id = parseId(taskId);
        Long tenantId = tenantResolver.currentTenantId();
        TaskEntity entity = taskRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        taskParamRepository.deleteByTenantIdAndTaskId(tenantId, id);
        taskRepository.delete(entity);
        taskSchedulerService.removeTask(taskId);
    }

    @Transactional
    public TaskInfo toggleTask(String taskId, boolean enabled) {
        Long id = parseId(taskId);
        Long tenantId = tenantResolver.currentTenantId();
        TaskEntity entity = taskRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        TaskEntity saved = taskRepository.save(entity);
        TaskInfo info = toTaskInfo(saved, loadParamMap(tenantId, id));
        if (enabled) {
            taskSchedulerService.scheduleTask(info);
        } else {
            taskSchedulerService.removeTask(taskId);
        }
        return info;
    }

    @Transactional
    public TaskExecutionRecord triggerTask(String taskId, TaskTriggerRequest request) {
        TaskExecutionRecord record =
                taskExecutionService.executeManual(taskId, request.getOperator(), request.getPayload());
        if (record == null) {
            throw new IllegalStateException("Task " + taskId + " is already running, manual trigger skipped");
        }
        return record;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskExecutionRecord> getExecutions(String taskId, int page, int size, String range) {
        Long id = parseId(taskId);
        Long tenantId = tenantResolver.currentTenantId();
        int resolvedPage = Math.max(page, 1) - 1;
        int resolvedSize = Math.max(size, 10);
        var pageable = org.springframework.data.domain.PageRequest.of(
                resolvedPage, resolvedSize, org.springframework.data.domain.Sort.by("scheduledTime").descending());
        java.time.LocalDateTime fromTime = resolveRangeStart(range);
        org.springframework.data.domain.Page<TaskInstanceEntity> pageResult;
        if (fromTime != null) {
            pageResult = taskInstanceRepository
                    .findByTenantIdAndTaskIdAndScheduledTimeGreaterThanEqualOrderByScheduledTimeDesc(
                            tenantId, id, fromTime, pageable);
        } else {
            pageResult =
                    taskInstanceRepository.findByTenantIdAndTaskIdOrderByScheduledTimeDesc(tenantId, id, pageable);
        }

        List<TaskExecutionRecord> records = pageResult.getContent().stream()
                .map(entity -> toExecutionRecord(entity, null))
                .collect(Collectors.toList());

        return PageResponse.<TaskExecutionRecord>builder()
                .records(records)
                .total(pageResult.getTotalElements())
                .page(pageResult.getNumber() + 1)
                .size(pageResult.getSize())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> suggestCron(String keyword) {
        List<Map<String, String>> suggestions = List.of(
                Map.of("expression", "0 0 2 * * ?", "desc", "Daily at 02:00"),
                Map.of("expression", "0 */30 * * * ?", "desc", "Every 30 minutes"),
                Map.of("expression", "0 0 0 * * MON", "desc", "Every Monday at 00:00"),
                Map.of("expression", "0 0 6 1 * ?", "desc", "First day of month at 06:00"));
        if (!StringUtils.hasText(keyword)) {
            return suggestions;
        }
        return suggestions.stream()
                .filter(item -> item.get("expression").contains(keyword) || item.get("desc").contains(keyword))
                .collect(Collectors.toList());
    }

    private LocalDateTime resolveRangeStart(String range) {
        if (!StringUtils.hasText(range)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String normalized = range.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "24h":
                return now.minusHours(24);
            case "48h":
                return now.minusHours(48);
            case "7d":
                return now.minusDays(7);
            case "30d":
                return now.minusDays(30);
            default:
                if (normalized.endsWith("h")) {
                    try {
                        long hours = Long.parseLong(normalized.substring(0, normalized.length() - 1));
                        if (hours > 0) {
                            return now.minusHours(Math.min(hours, 24 * 30));
                        }
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
                if (normalized.endsWith("d")) {
                    try {
                        long days = Long.parseLong(normalized.substring(0, normalized.length() - 1));
                        if (days > 0) {
                            return now.minusDays(Math.min(days, 180));
                        }
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
                return null;
        }
    }

    private void validateTaskRequest(TaskRequest request) {
        if (request.getType() == null) {
            throw new IllegalArgumentException("Task type is required");
        }
        if (request.getType() == TaskType.CRON) {
            if (!StringUtils.hasText(request.getCronExpr())) {
                throw new IllegalArgumentException("Cron expression is required for CRON tasks");
            }
            if (!CronExpression.isValidExpression(request.getCronExpr())) {
                throw new IllegalArgumentException("Invalid Cron expression: " + request.getCronExpr());
            }
        }
    }

    private void applyTaskRequest(TaskEntity entity, TaskRequest request) {
        entity.setName(request.getName());
        entity.setBizGroup(request.getGroup());
        entity.setDescription(request.getDescription());
        entity.setType(request.getType() != null ? request.getType().name() : TaskType.CRON.name());
        entity.setHandler(StringUtils.hasText(request.getHandler()) ? request.getHandler() : "");
        entity.setCronExpr(request.getCronExpr());
        entity.setTimeZone(StringUtils.hasText(request.getTimeZone()) ? request.getTimeZone() : "Asia/Shanghai");
        entity.setShardCount(1);
        entity.setRetryMax(request.getMaxRetry());
        entity.setRetryBackoffMs(60000);
        entity.setConcurrencyPolicy(
                request.getRouteStrategy() != null ? request.getRouteStrategy().name() : RouteStrategy.ROUND_ROBIN.name());
    }

    private Map<String, String> loadParamMap(Long tenantId, Long taskId) {
        return taskParamRepository.findByTenantIdAndTaskId(tenantId, taskId).stream()
                .collect(Collectors.toMap(TaskParamEntity::getParamKey, TaskParamEntity::getParamValue, (a, b) -> b));
    }

    private void persistParams(Long tenantId, Long taskId, TaskRequest request) {
        List<TaskParamEntity> params = new ArrayList<>();
        params.add(buildParam(tenantId, taskId, PARAM_OWNER, request.getOwner()));
        List<String> sanitizedTags = request.getTags() == null
                ? List.of()
                : request.getTags().stream()
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
        params.add(buildParam(tenantId, taskId, PARAM_TAGS, String.join(",", sanitizedTags)));
        params.add(buildParam(
                tenantId,
                taskId,
                PARAM_ROUTE_STRATEGY,
                request.getRouteStrategy() != null ? request.getRouteStrategy().name() : RouteStrategy.ROUND_ROBIN.name()));
        params.add(buildParam(
                tenantId,
                taskId,
                PARAM_RETRY_POLICY,
                request.getRetryPolicy() != null ? request.getRetryPolicy().name() : RetryPolicy.EXP_BACKOFF.name()));
        params.add(buildParam(tenantId, taskId, PARAM_IDEMPOTENT_KEY, request.getIdempotentKey()));
        params.add(buildParam(tenantId, taskId, PARAM_ALERT_ENABLED, Boolean.toString(request.isAlertEnabled())));
        params.add(buildParam(tenantId, taskId, PARAM_TIMEOUT, Integer.toString(request.getTimeout())));
        // 保存 executorType
        params.add(buildParam(
                tenantId,
                taskId,
                "executorType",
                request.getExecutorType() != null ? request.getExecutorType().name() : ExecutorType.HTTP.name()));

        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            try {
                params.add(buildParam(tenantId, taskId, PARAM_PARAMETERS, objectMapper.writeValueAsString(request.getParameters())));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize task parameters", e);
            }
        }

        List<TaskParamEntity> filtered = params.stream()
                .filter(param -> StringUtils.hasText(param.getParamValue()))
                .collect(Collectors.toList());

        taskParamRepository.saveAll(filtered);
    }

    private TaskParamEntity buildParam(Long tenantId, Long taskId, String key, String value) {
        TaskParamEntity entity = new TaskParamEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(taskId);
        entity.setParamKey(key);
        entity.setParamValue(value);
        return entity;
    }

    private List<TaskDependency> loadDependencies(Long tenantId, Long taskId) {
        List<TaskDependencyEntity> dependencyEntities = taskDependencyRepository.findByTenantIdAndTaskId(tenantId, taskId);
        if (CollectionUtils.isEmpty(dependencyEntities)) {
            return Collections.emptyList();
        }
        Set<Long> dependencyTaskIds = dependencyEntities.stream()
                .map(TaskDependencyEntity::getDependsOnTaskId)
                .collect(Collectors.toSet());
        Map<Long, TaskEntity> dependencyTasks = taskRepository.findAllById(dependencyTaskIds).stream()
                .filter(task -> Objects.equals(task.getTenantId(), tenantId))
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity()));

        return dependencyEntities.stream()
                .map(entity -> {
                    TaskEntity dependencyTask = dependencyTasks.get(entity.getDependsOnTaskId());
                    TaskInstanceEntity latestInstance = null;
                    if (dependencyTask != null) {
                        latestInstance = taskInstanceRepository
                                .findFirstByTenantIdAndTaskIdOrderByScheduledTimeDesc(tenantId, dependencyTask.getId())
                                .orElse(null);
                    }
                    TaskStatus status = latestInstance != null
                            ? mapStatus(latestInstance.getStatus())
                            : dependencyTask != null && Boolean.TRUE.equals(dependencyTask.getEnabled())
                                    ? TaskStatus.ENABLED
                                    : TaskStatus.UNKNOWN;
                    String node = latestInstance != null && latestInstance.getExecutorNodeId() != null
                            ? "node-" + latestInstance.getExecutorNodeId()
                            : null;

                    return TaskDependency.builder()
                            .id(String.valueOf(entity.getDependsOnTaskId()))
                            .name(dependencyTask != null ? dependencyTask.getName() : "Task-" + entity.getDependsOnTaskId())
                            .triggerType(dependencyTask != null ? dependencyTask.getType() : null)
                            .status(status)
                            .node(node)
                            .cronExpr(dependencyTask != null ? dependencyTask.getCronExpr() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private TaskInfo toTaskInfo(TaskEntity entity, Map<String, String> params) {
        Long tenantId = entity.getTenantId();
        String tenantCode = tenantResolver.resolveTenantCode(tenantId);
        String tenantName = tenantResolver.resolveTenantName(tenantId);
        List<TaskDependency> dependencyInfos = loadDependencies(tenantId, entity.getId());
        TaskInfo.TaskInfoBuilder builder = TaskInfo.builder()
                .id(String.valueOf(entity.getId()))
                .tenantId(tenantId)
                .tenantCode(tenantCode)
                .tenantName(tenantName)
                .name(entity.getName())
                .group(entity.getBizGroup())
                .description(entity.getDescription())
                .type(resolveTaskType(entity.getType()))
                .executorType(params.containsKey("executorType") 
                    ? resolveExecutorType(params.get("executorType"))
                    : resolveExecutorType(entity.getHandler()))
                .handler(entity.getHandler())
                .cronExpr(entity.getCronExpr())
                .timeZone(entity.getTimeZone())
                .routeStrategy(resolveRouteStrategy(params.get(PARAM_ROUTE_STRATEGY)))
                .retryPolicy(resolveRetryPolicy(params.get(PARAM_RETRY_POLICY)))
                .maxRetry(entity.getRetryMax() != null ? entity.getRetryMax() : 0)
                .timeout(getTimeoutFromParams(params))
                .owner(params.get(PARAM_OWNER))
                .tags(parseTags(params.get(PARAM_TAGS)))
                .idempotentKey(params.get(PARAM_IDEMPOTENT_KEY))
                .description(entity.getDescription())
                .alertEnabled(Boolean.parseBoolean(params.getOrDefault(PARAM_ALERT_ENABLED, "false")))
                .status(entity.getEnabled() != null && entity.getEnabled() ? TaskStatus.ENABLED : TaskStatus.DISABLED)
                .enabled(entity.getEnabled() != null ? entity.getEnabled() : Boolean.TRUE)
                .createdAt(entity.getCreatedAt() != null ? toOffsetDateTime(entity.getCreatedAt()) : null)
                .updatedAt(entity.getUpdatedAt() != null ? toOffsetDateTime(entity.getUpdatedAt()) : null)
                .dependencies(dependencyInfos);

        builder.parameters(parseParameters(params.get(PARAM_PARAMETERS)));

        taskInstanceRepository
                .findFirstByTenantIdAndTaskIdOrderByScheduledTimeDesc(tenantId, entity.getId())
                .ifPresent(instance -> builder
                        .lastTrigger(toOffsetDateTime(instance.getTriggeredAt(), instance.getScheduledTime()))
                        .lastNode(instance.getExecutorNodeId() != null
                                ? "node-" + instance.getExecutorNodeId()
                                : null)
                        .status(mapStatus(instance.getStatus())));

        return builder.build();
    }

    private boolean filterByKeyword(TaskInfo info, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return (info.getName() != null && info.getName().toLowerCase(Locale.ROOT).contains(lower))
                || (info.getOwner() != null && info.getOwner().toLowerCase(Locale.ROOT).contains(lower))
                || (info.getTags() != null
                        && info.getTags().stream()
                                .anyMatch(tag -> tag != null && tag.toLowerCase(Locale.ROOT).contains(lower)));
    }

    private boolean filterByStatus(TaskInfo info, String status) {
        if (!StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status)) {
            return true;
        }
        if ("ENABLED".equalsIgnoreCase(status)) {
            return Boolean.TRUE.equals(info.isEnabled());
        }
        if ("DISABLED".equalsIgnoreCase(status)) {
            return !Boolean.TRUE.equals(info.isEnabled());
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return info.getStatus() == TaskStatus.FAILED || info.getStatus() == TaskStatus.DEGRADED;
        }
        return true;
    }

    private boolean filterByOwner(TaskInfo info, String owner) {
        if (!StringUtils.hasText(owner)) {
            return true;
        }
        return Objects.equals(info.getOwner(), owner);
    }

    private boolean filterByTags(TaskInfo info, List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return true;
        }
        if (CollectionUtils.isEmpty(info.getTags())) {
            return false;
        }
        return info.getTags().containsAll(tags);
    }

    private TaskType resolveTaskType(String value) {
        String resolved = defaultString(value, TaskType.CRON.name());
        try {
            return TaskType.valueOf(resolved);
        } catch (IllegalArgumentException ex) {
            return TaskType.CRON;
        }
    }

    private ExecutorType resolveExecutorType(String value) {
        String resolved = defaultString(value, ExecutorType.GRPC.name());
        try {
            return ExecutorType.valueOf(resolved);
        } catch (IllegalArgumentException ex) {
            return ExecutorType.GRPC;
        }
    }

    private RouteStrategy resolveRouteStrategy(String value) {
        String resolved = defaultString(value, RouteStrategy.ROUND_ROBIN.name());
        try {
            return RouteStrategy.valueOf(resolved);
        } catch (IllegalArgumentException ex) {
            return RouteStrategy.ROUND_ROBIN;
        }
    }

    private RetryPolicy resolveRetryPolicy(String value) {
        String resolved = defaultString(value, RetryPolicy.EXP_BACKOFF.name());
        try {
            return RetryPolicy.valueOf(resolved);
        } catch (IllegalArgumentException ex) {
            return RetryPolicy.EXP_BACKOFF;
        }
    }

    private List<String> parseTags(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private Map<String, Object> parseParameters(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private TaskExecutionRecord toExecutionRecord(TaskInstanceEntity entity, String taskName) {
        return TaskExecutionRecord.builder()
                .id(String.valueOf(entity.getId()))
                .taskId(String.valueOf(entity.getTaskId()))
                .triggerTime(toOffsetDateTime(entity.getTriggeredAt(), entity.getScheduledTime()))
                .node(entity.getExecutorNodeId() != null ? "node-" + entity.getExecutorNodeId() : null)
                .status(mapStatus(entity.getStatus()))
                .duration(calculateDuration(entity))
                .retry(Optional.ofNullable(entity.getAttempts()).orElse(0))
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
        String normalized = status.toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized)) {
            return TaskStatus.SUCCESS;
        }
        if ("FAILED".equals(normalized)) {
            return TaskStatus.FAILED;
        }
        if ("RUNNING".equals(normalized)) {
            return TaskStatus.RUNNING;
        }
        if ("SCHEDULED".equals(normalized) || "PENDING".equals(normalized)) {
            return TaskStatus.SCHEDULED;
        }
        return TaskStatus.UNKNOWN;
    }

    private long calculateDuration(TaskInstanceEntity entity) {
        if (entity.getTriggeredAt() == null || entity.getFinishedAt() == null) {
            return 0L;
        }
        return Duration.between(
                        entity.getTriggeredAt(),
                        entity.getFinishedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .toMillis();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime triggeredAt, LocalDateTime scheduledTime) {
        LocalDateTime reference = triggeredAt != null ? triggeredAt : scheduledTime;
        if (reference == null) {
            return null;
        }
        return reference.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null
                ? null
                : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private int getTimeoutFromParams(Map<String, String> params) {
        String value = params.get(PARAM_TIMEOUT);
        if (!StringUtils.hasText(value)) {
            return 300;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 300;
        }
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Task ID is invalid: " + id);
        }
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private List<Map<String, Object>> buildTrendData(Long tenantId, LocalDateTime from) {
        List<TaskInstanceEntity> instances =
                taskInstanceRepository.findTop10ByTenantIdOrderByScheduledTimeDesc(tenantId);
        if (instances.isEmpty()) {
            return fallbackTrend();
        }
        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        for (TaskInstanceEntity instance : instances) {
            LocalDateTime time = Optional.ofNullable(instance.getScheduledTime()).orElse(from);
            String bucketKey = time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            Map<String, Object> bucket = buckets.computeIfAbsent(bucketKey, key -> {
                Map<String, Object> map = new HashMap<>();
                map.put("time", key);
                map.put("successRate", 0);
                map.put("failedRate", 0);
                map.put("total", 0);
                return map;
            });
            bucket.put("total", ((int) bucket.get("total")) + 1);
            if ("SUCCESS".equalsIgnoreCase(instance.getStatus())) {
                bucket.put("successRate", ((int) bucket.get("successRate")) + 1);
            } else if ("FAILED".equalsIgnoreCase(instance.getStatus())) {
                bucket.put("failedRate", ((int) bucket.get("failedRate")) + 1);
            }
        }

        return buckets.values().stream()
                .map(bucket -> {
                    int total = (int) bucket.get("total");
                    int success = (int) bucket.get("successRate");
                    int failed = (int) bucket.get("failedRate");
                    return Map.<String, Object>of(
                            "time", bucket.get("time"),
                            "successRate", total == 0 ? 0 : Math.round(success * 100.0 / total),
                            "failedRate", total == 0 ? 0 : Math.round(failed * 100.0 / total));
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> fallbackTrend() {
        return List.of(
                Map.of("time", "00:00", "successRate", 95, "failedRate", 5),
                Map.of("time", "08:00", "successRate", 97, "failedRate", 3),
                Map.of("time", "16:00", "successRate", 96, "failedRate", 4));
    }

    private List<Map<String, Object>> buildRecentEvents(Long tenantId) {
        return taskInstanceRepository.findTop10ByTenantIdOrderByScheduledTimeDesc(tenantId).stream()
                .map(instance -> Map.<String, Object>of(
                        "id", instance.getInstanceId(),
                        "time", formatDateTime(instance.getScheduledTime()),
                        "type", mapTimelineType(instance.getStatus()),
                        "title", buildEventTitle(instance),
                        "tagLabel", instance.getStatus(),
                        "tagType", mapTagType(instance.getStatus()),
                        "desc", Optional.ofNullable(instance.getResult()).orElse("Execution finished")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopFailedTasks(Long tenantId) {
        return taskInstanceRepository.findTop10ByTenantIdOrderByScheduledTimeDesc(tenantId).stream()
                .filter(instance -> "FAILED".equalsIgnoreCase(instance.getStatus()))
                .collect(Collectors.groupingBy(TaskInstanceEntity::getTaskId))
                .entrySet()
                .stream()
                .map(entry -> {
                    Long taskId = entry.getKey();
                    List<TaskInstanceEntity> failures = entry.getValue();
                    TaskEntity task =
                            taskRepository.findByTenantIdAndId(tenantId, taskId).orElse(null);
                    String owner = Optional.ofNullable(task)
                            .map(entity -> loadParamMap(tenantId, entity.getId()).get(PARAM_OWNER))
                            .orElse("-");
                    TaskInstanceEntity latest = failures.stream()
                            .max(Comparator.comparing(TaskInstanceEntity::getScheduledTime, Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    return Map.<String, Object>of(
                            "id", taskId != null ? String.valueOf(taskId) : UUID.randomUUID().toString(),
                            "name", task != null ? task.getName() : "Unknown task",
                            "owner", owner,
                            "failed", failures.size(),
                            "lastFailed", latest != null ? formatDateTime(latest.getScheduledTime()) : "-");
                })
                .collect(Collectors.toList());
    }

    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(DATE_TIME_FORMATTER);
    }

    private String mapTimelineType(String status) {
        if (!StringUtils.hasText(status)) {
            return "info";
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized)) {
            return "primary";
        }
        if ("FAILED".equals(normalized)) {
            return "danger";
        }
        if ("RUNNING".equals(normalized)) {
            return "success";
        }
        return "warning";
    }

    private String mapTagType(String status) {
        if (!StringUtils.hasText(status)) {
            return "info";
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized)) {
            return "success";
        }
        if ("FAILED".equals(normalized)) {
            return "danger";
        }
        if ("RUNNING".equals(normalized)) {
            return "warning";
        }
        return "info";
    }

    private String buildEventTitle(TaskInstanceEntity instance) {
        TaskEntity task = null;
        Long tenantId = null;
        try {
            tenantId = instance.getTenantId();
        } catch (Exception ignored) {
            // ignore missing tenant info
        }
        if (tenantId != null) {
            task = taskRepository.findByTenantIdAndId(tenantId, instance.getTaskId()).orElse(null);
        }
        if (task == null) {
            task = taskRepository.findById(instance.getTaskId()).orElse(null);
        }
        String taskName = task != null ? task.getName() : "Task";
        if ("FAILED".equalsIgnoreCase(instance.getStatus())) {
            return taskName + " execution failed";
        }
        if ("SUCCESS".equalsIgnoreCase(instance.getStatus())) {
            return taskName + " execution succeeded";
        }
        if ("RUNNING".equalsIgnoreCase(instance.getStatus())) {
            return taskName + " is running";
        }
        return taskName + " status updated";
    }
}
