package com.skytask.scheduler;

import com.skytask.model.TaskInfo;
import com.skytask.model.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskSchedulerService {

    private static final String JOB_PREFIX = "task-job-";
    private static final String TRIGGER_PREFIX = "task-trigger-";

    private final Scheduler scheduler;

    public void scheduleTask(TaskInfo task) {
        try {
            JobKey jobKey = jobKey(task.getId());
            TriggerKey triggerKey = triggerKey(task.getId());

            JobDetail jobDetail = JobBuilder.newJob(TaskQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(TaskQuartzJob.TASK_ID_KEY, task.getId())
                    .usingJobData(TaskQuartzJob.TENANT_CODE_KEY, task.getTenantCode())
                    .usingJobData(TaskQuartzJob.TENANT_ID_KEY, task.getTenantId() != null ? task.getTenantId().toString() : "")
                    .storeDurably()
                    .build();

            scheduler.addJob(jobDetail, true);
            scheduler.unscheduleJob(triggerKey);

            if (!task.isEnabled()) {
                log.info("Task {} is disabled, job stored without trigger", task.getId());
                return;
            }

            Trigger trigger = buildTrigger(task, triggerKey, jobDetail);
            if (trigger != null) {
                scheduler.scheduleJob(trigger);
                log.info("Task {} scheduled with trigger {}", task.getId(), trigger.getKey());
            } else {
                log.warn("Task {} has no valid schedule definition, skipping trigger creation", task.getId());
            }
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to schedule task " + task.getId(), ex);
        }
    }

    public void removeTask(String taskId) {
        try {
            scheduler.deleteJob(jobKey(taskId));
            log.info("Removed Quartz job for task {}", taskId);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to remove scheduled task " + taskId, ex);
        }
    }

    public void triggerNow(String taskId) {
        try {
            scheduler.triggerJob(jobKey(taskId));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to trigger task " + taskId, ex);
        }
    }

    public void scheduleRetry(String taskId, Long tenantId, String tenantCode, Date fireTime, int attempt) {
        try {
            JobKey jobKey = jobKey(taskId);
            if (!scheduler.checkExists(jobKey)) {
                log.warn("Skip retry scheduling for task {} because Quartz job is missing", taskId);
                return;
            }
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            JobDataMap dataMap = jobDetail != null ? jobDetail.getJobDataMap() : new JobDataMap();
            String resolvedTenantCode = StringUtils.hasText(tenantCode)
                    ? tenantCode
                    : dataMap.getString(TaskQuartzJob.TENANT_CODE_KEY);
            String tenantIdStr = tenantId != null ? tenantId.toString() : dataMap.getString(TaskQuartzJob.TENANT_ID_KEY);
            TriggerKey retryTriggerKey =
                    TriggerKey.triggerKey(TRIGGER_PREFIX + "retry-" + taskId + "-" + fireTime.getTime());
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(retryTriggerKey)
                    .forJob(jobKey)
                    .usingJobData(TaskQuartzJob.TASK_ID_KEY, taskId)
                    .usingJobData(TaskQuartzJob.RETRY_ATTEMPT_KEY, attempt)
                    .usingJobData(TaskQuartzJob.TENANT_CODE_KEY, resolvedTenantCode)
                    .usingJobData(TaskQuartzJob.TENANT_ID_KEY, tenantIdStr != null ? tenantIdStr : "")
                    .startAt(fireTime)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
                    .build();
            scheduler.scheduleJob(trigger);
            log.info("Retry for task {} scheduled at {} (attempt #{})", taskId, fireTime, attempt);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to schedule retry for task " + taskId, ex);
        }
    }

    private Trigger buildTrigger(TaskInfo task, TriggerKey triggerKey, JobDetail jobDetail) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .usingJobData(TaskQuartzJob.TENANT_CODE_KEY, task.getTenantCode())
                .usingJobData(TaskQuartzJob.TENANT_ID_KEY, task.getTenantId() != null ? task.getTenantId().toString() : "")
                .startNow();

        if (task.getType() == TaskType.CRON && StringUtils.hasText(task.getCronExpr())) {
            return builder.withSchedule(
                            CronScheduleBuilder.cronSchedule(task.getCronExpr())
                                    .inTimeZone(task.getTimeZone() != null
                                            ? java.util.TimeZone.getTimeZone(task.getTimeZone())
                                            : java.util.TimeZone.getDefault()))
                    .build();
        }

        if (task.getType() == TaskType.FIXED_RATE) {
            long intervalMillis = Math.max(task.getTimeout(), 60) * 1000L;
            return builder.withSchedule(
                            SimpleScheduleBuilder.simpleSchedule()
                                    .withIntervalInMilliseconds(intervalMillis)
                                    .repeatForever())
                    .build();
        }

        if (task.getType() == TaskType.ONE_TIME) {
            Date startAt = Date.from(Instant.now().plusSeconds(Math.max(task.getTimeout(), 60)));
            return builder.startAt(startAt).withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)).build();
        }

        return null;
    }

    private JobKey jobKey(String taskId) {
        return JobKey.jobKey(JOB_PREFIX + taskId);
    }

    private TriggerKey triggerKey(String taskId) {
        return TriggerKey.triggerKey(TRIGGER_PREFIX + taskId);
    }
}
