package com.skytask.controller;

import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单的测试控制器 - 用于验证基本功能
 */
@RestController
@RequestMapping("/api/simple-test")
@Slf4j
public class SimpleTestController {

    @PostMapping("/hello")
    public WorkerExecutionResult hello(@RequestBody WorkerExecuteRequest request) {
        log.info("🎯 Simple test endpoint called. Instance: {}", request.getInstanceId());
        
        return WorkerExecutionResult.builder()
            .taskId(request.getTaskId())
            .instanceId(request.getInstanceId())
            .status("SUCCESS")
            .message("Hello from simple test endpoint! 🎉")
            .attempt(request.getAttempt())
            .durationMillis(50)
            .build();
    }
}


