package com.skytask.scheduler;

import com.skytask.context.TenantContextHolder;
import com.skytask.service.TaskExecutionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TaskQuartzJob implements Job {

    public static final String TASK_ID_KEY = "taskId";
    public static final String RETRY_ATTEMPT_KEY = "retryAttempt";
    public static final String TENANT_CODE_KEY = "tenantCode";
    public static final String TENANT_ID_KEY = "tenantId";

    @Setter(onMethod_ = @Autowired)
    private TaskExecutionService taskExecutionService;

    public TaskQuartzJob() {
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String taskId = dataMap.getString(TASK_ID_KEY);
        if (taskId == null) {
            log.warn("Quartz job triggered without task id");
            return;
        }
        String tenantCode = dataMap.getString(TENANT_CODE_KEY);
        int retryAttempt = dataMap.containsKey(RETRY_ATTEMPT_KEY) ? dataMap.getInt(RETRY_ATTEMPT_KEY) : 0;
        try {
            if (StringUtils.hasText(tenantCode)) {
                TenantContextHolder.setTenantCode(tenantCode);
            }
            taskExecutionService.executeScheduled(taskId, context.getScheduledFireTime(), retryAttempt);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
