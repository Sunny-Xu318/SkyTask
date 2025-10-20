package com.skytask.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.skytask.dto.TaskRequest;
import com.skytask.model.ExecutorType;
import com.skytask.model.RetryPolicy;
import com.skytask.model.RouteStrategy;
import com.skytask.model.TaskInfo;
import com.skytask.model.TaskType;
import com.skytask.repository.TaskRepository;
import com.skytask.scheduler.TaskSchedulerService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @MockBean
    private TaskSchedulerService taskSchedulerService;

    @MockBean
    private TaskExecutionService taskExecutionService;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        reset(taskSchedulerService, taskExecutionService);
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
    }

    @Test
    void createTask_shouldPersistAndSchedule() {
        TaskRequest request = buildCronTaskRequest("test-demo-task");

        TaskInfo info = taskService.createTask(request);

        assertThat(info.getId()).isNotBlank();
        assertThat(taskRepository.count()).isEqualTo(1);
        verify(taskSchedulerService).scheduleTask(any());
    }

    @Test
    void updateTask_shouldReschedule() {
        TaskInfo created = taskService.createTask(buildCronTaskRequest("test-update-task"));
        verify(taskSchedulerService).scheduleTask(any());
        reset(taskSchedulerService);

        TaskRequest update = buildCronTaskRequest("test-update-task");
        update.setCronExpr("0 0/30 * * * ?");
        TaskInfo updated = taskService.updateTask(created.getId(), update);

        assertThat(updated.getCronExpr()).isEqualTo("0 0/30 * * * ?");
        verify(taskSchedulerService).scheduleTask(any());
    }

    @Test
    void toggleTask_shouldDisableScheduling() {
        TaskInfo created = taskService.createTask(buildCronTaskRequest("test-toggle-task"));
        reset(taskSchedulerService);

        TaskInfo disabled = taskService.toggleTask(created.getId(), false);

        assertThat(disabled.isEnabled()).isFalse();
        verify(taskSchedulerService).removeTask(created.getId());
    }

    private TaskRequest buildCronTaskRequest(String name) {
        TaskRequest request = new TaskRequest();
        request.setName(name);
        request.setGroup("TEST_GROUP");
        request.setDescription("JUnit generated task");
        request.setType(TaskType.CRON);
        request.setExecutorType(ExecutorType.GRPC);
        request.setCronExpr("0 0/15 * * * ?");
        request.setTimeZone("Asia/Shanghai");
        request.setRouteStrategy(RouteStrategy.ROUND_ROBIN);
        request.setRetryPolicy(RetryPolicy.EXP_BACKOFF);
        request.setMaxRetry(2);
        request.setTimeout(120);
        request.setOwner("junit");
        request.setTags(List.of("test"));
        request.setIdempotentKey("test-${timestamp}");
        request.setParameters(Map.of("demo", "value"));
        request.setAlertEnabled(false);
        return request;
    }
}
