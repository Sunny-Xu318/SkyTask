package com.skytask.service;

import com.skytask.entity.TaskEntity;
import com.skytask.repository.TaskRepository;
import com.skytask.scheduler.TaskSchedulerService;
import com.skytask.service.event.TaskFailureEscalationEvent;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskAutoRecoveryListener {

    private final TaskRepository taskRepository;
    private final TaskSchedulerService taskSchedulerService;
    private final AlertNotificationService alertNotificationService;

    @EventListener
    @Transactional
    public void onTaskFailure(TaskFailureEscalationEvent event) {
        Optional<TaskEntity> optionalTask = taskRepository.findById(event.getTaskId());
        if (optionalTask.isEmpty()) {
            log.warn("Received escalation for unknown task {}", event.getTaskId());
            return;
        }
        TaskEntity task = optionalTask.get();
        if (Boolean.TRUE.equals(task.getEnabled())) {
            task.setEnabled(false);
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);
            taskSchedulerService.removeTask(String.valueOf(task.getId()));
            log.warn("Task {} ({}) disabled automatically due to failure rate {}%",
                    task.getId(), task.getName(), String.format(java.util.Locale.ENGLISH, "%.2f", event.getFailureRate()));
        }
        alertNotificationService.notifyFailure(task.getId(), task.getName(), event.getFailureRate(), event.getSampleCount());
    }
}
