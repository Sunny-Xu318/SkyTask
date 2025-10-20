package com.skytask.controller;

import com.skytask.dto.WorkerExecutionResult;
import com.skytask.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/scheduler")
@RequiredArgsConstructor
public class WorkerCallbackController {

    private final TaskExecutionService taskExecutionService;

    @PostMapping("/tasks/{taskId}/result")
    public ResponseEntity<Void> receiveResult(
            @PathVariable("taskId") String taskId, @RequestBody WorkerExecutionResult result) {
        if (!StringUtils.hasText(result.getTaskId())) {
            result.setTaskId(taskId);
        }
        taskExecutionService.handleWorkerResult(result);
        return ResponseEntity.accepted().build();
    }
}
