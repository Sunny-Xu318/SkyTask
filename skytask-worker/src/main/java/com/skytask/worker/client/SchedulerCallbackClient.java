package com.skytask.worker.client;

import com.skytask.worker.dto.TaskExecutionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "skytask-scheduler", path = "/internal/scheduler")
public interface SchedulerCallbackClient {

    @PostMapping("/tasks/{taskId}/result")
    void reportResult(
            @PathVariable("taskId") String taskId,
            @RequestBody TaskExecutionResult result,
            @RequestHeader("X-SkyTask-Tenant") String tenantCode);
}
