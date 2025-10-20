package com.skytask.client;

import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "skytask-worker", path = "/api/worker/tasks", fallbackFactory = WorkerClientFallbackFactory.class)
public interface WorkerClient {

    @PostMapping("/{taskId}/execute")
    WorkerExecutionResult executeTask(
            @PathVariable("taskId") String taskId,
            @RequestBody WorkerExecuteRequest request,
            @RequestHeader("X-SkyTask-Tenant") String tenantCode);
}
