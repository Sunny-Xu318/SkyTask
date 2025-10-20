package com.skytask.worker.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.skytask.worker.client.SchedulerCallbackClient;
import com.skytask.worker.dto.TaskExecutionPayload;
import com.skytask.worker.dto.TaskExecutionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerExecutionService {

    private final SchedulerCallbackClient schedulerCallbackClient;
    private final MeterRegistry meterRegistry;

    @Value("${worker.execution.simulated-delay-ms:750}")
    private long simulatedDelayMs;

    private Counter executionSuccessCounter;
    private Counter executionFailureCounter;
    private Timer workerExecutionTimer;

    @PostConstruct
    void initMetrics() {
        this.executionSuccessCounter = Counter.builder("skytask_worker_executions_total")
                .tag("result", "success")
                .description("Number of successful worker executions")
                .register(meterRegistry);
        this.executionFailureCounter = Counter.builder("skytask_worker_executions_total")
                .tag("result", "failure")
                .description("Number of failed worker executions")
                .register(meterRegistry);
        this.workerExecutionTimer = Timer.builder("skytask_worker_execution_duration")
                .description("Execution time spent by worker processing a task")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @SentinelResource(value = "workerExecuteTask")
    public TaskExecutionResult executeTask(String taskId, TaskExecutionPayload payload, String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new IllegalArgumentException("Missing tenant header for task execution callback");
        }
        long start = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info(
                "Receive execute request for task {} instance {} attempt {}",
                taskId,
                payload.getInstanceId(),
                payload.getAttempt());

        try {
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, simulatedDelayMs / 2));
            Thread.sleep(simulatedDelayMs + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker execution interrupted for task {}", taskId);
        }

        boolean success = ThreadLocalRandom.current().nextInt(100) > 10;
        String status = success ? "SUCCESS" : "FAILED";
        String message = success ? "Worker execution finished" : "Worker execution failed";

        TaskExecutionResult result = TaskExecutionResult.builder()
                .taskId(taskId)
                .instanceId(payload.getInstanceId())
                .status(status)
                .message(message)
                .durationMillis(System.currentTimeMillis() - start)
                .attempt(payload.getAttempt())
                .build();

        if (success) {
            executionSuccessCounter.increment();
        } else {
            executionFailureCounter.increment();
        }
        sample.stop(workerExecutionTimer);

        try {
            schedulerCallbackClient.reportResult(taskId, result, tenantCode);
        } catch (Exception ex) {
            log.error("Failed to callback scheduler for task {} - {}", taskId, ex.getMessage(), ex);
        }

        return result;
    }
}
