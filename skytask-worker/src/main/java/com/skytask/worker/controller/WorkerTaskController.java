package com.skytask.worker.controller;

import com.skytask.worker.dto.TaskExecutionPayload;
import com.skytask.worker.dto.TaskExecutionResult;
import com.skytask.worker.service.WorkerExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worker/tasks")
@RequiredArgsConstructor
public class WorkerTaskController {

    private final WorkerExecutionService workerExecutionService;

    @PostMapping("/{taskId}/execute")
    public ResponseEntity<TaskExecutionResult> execute(
            @PathVariable("taskId") String taskId,
            @RequestBody TaskExecutionPayload payload,
            @RequestHeader(value = "X-SkyTask-Tenant") String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TaskExecutionResult.builder()
                            .taskId(taskId)
                            .instanceId(payload.getInstanceId())
                            .status("REJECTED")
                            .message("Missing tenant header")
                            .attempt(payload.getAttempt())
                            .durationMillis(0)
                            .build());
        }
        TaskExecutionResult result = workerExecutionService.executeTask(taskId, payload, tenantCode);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
