package com.skytask.bootstrap;

import com.skytask.dto.TaskRequest;
import com.skytask.model.ExecutorType;
import com.skytask.model.RetryPolicy;
import com.skytask.model.RouteStrategy;
import com.skytask.model.TaskType;
import com.skytask.repository.TaskRepository;
import com.skytask.service.TaskService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "skytask.data", name = "init-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TaskRepository taskRepository;
    private final TaskService taskService;

    @Override
    @Transactional
    public void run(String... args) {
        if (taskRepository.count() > 0) {
            log.info("Skip demo data seeding (task table already contains records)");
            return;
        }
        log.info("Seeding demo tasks for SkyTask...");
        try {
            seedCronTask();
            seedFixedRateTask();
            log.info("Demo tasks seeded successfully");
        } catch (Exception ex) {
            log.warn("Failed to seed demo tasks: {}", ex.getMessage());
        }
    }

    private void seedCronTask() {
        TaskRequest request = new TaskRequest();
        request.setName("demo-nightly-report");
        request.setGroup("DEMO_REPORT");
        request.setDescription("Generate nightly demo report");
        request.setType(TaskType.CRON);
        request.setExecutorType(ExecutorType.GRPC);
        request.setCronExpr("0 0 1 * * ?");
        request.setTimeZone("Asia/Shanghai");
        request.setRouteStrategy(RouteStrategy.ROUND_ROBIN);
        request.setRetryPolicy(RetryPolicy.EXP_BACKOFF);
        request.setMaxRetry(3);
        request.setTimeout(600);
        request.setOwner("demo-user");
        request.setTags(List.of("demo", "report"));
        request.setIdempotentKey("report-${date}");
        request.setParameters(Map.of("templateId", "nightly-demo"));
        request.setAlertEnabled(true);
        taskService.createTask(request);
    }

    private void seedFixedRateTask() {
        TaskRequest request = new TaskRequest();
        request.setName("demo-cache-refresh");
        request.setGroup("DEMO_CACHE");
        request.setDescription("Refresh demo cache periodically");
        request.setType(TaskType.FIXED_RATE);
        request.setExecutorType(ExecutorType.HTTP);
        request.setTimeZone("Asia/Shanghai");
        request.setRouteStrategy(RouteStrategy.SHARDING);
        request.setRetryPolicy(RetryPolicy.FIXED_INTERVAL);
        request.setMaxRetry(2);
        request.setTimeout(180);
        request.setOwner("demo-ops");
        request.setTags(List.of("demo", "cache"));
        request.setIdempotentKey("cache-${shard}");
        request.setParameters(Map.of("endpoint", "https://demo.example.com/cache/refresh"));
        request.setAlertEnabled(false);
        taskService.createTask(request);
    }
}
