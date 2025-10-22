package com.skytask.controller;

import com.skytask.annotation.RequirePermission;
import com.skytask.dto.TaskRequest;
import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 测试控制器 - 用于调试任务创建和执行
 */
@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {

    @PostMapping("/task-request")
    @RequirePermission("task:read")
    public Map<String, Object> testTaskRequest(@RequestBody Map<String, Object> rawRequest) {
        log.info("Received raw request: {}", rawRequest);
        
        // 检查各个字段
        Map<String, Object> result = Map.of(
            "received", rawRequest,
            "name", rawRequest.get("name"),
            "group", rawRequest.get("group"),
            "type", rawRequest.get("type"),
            "executorType", rawRequest.get("executorType"),
            "handler", rawRequest.get("handler"),
            "routeStrategy", rawRequest.get("routeStrategy"),
            "retryPolicy", rawRequest.get("retryPolicy"),
            "owner", rawRequest.get("owner"),
            "cronExpr", rawRequest.get("cronExpr")
        );
        
        log.info("Parsed result: {}", result);
        return result;
    }

    @PostMapping("/task-request-validated")
    @RequirePermission("task:read")
    public Map<String, Object> testTaskRequestValidated(@Valid @RequestBody TaskRequest request) {
        log.info("Received validated TaskRequest: {}", request);
        
        return Map.of(
            "success", true,
            "taskName", request.getName(),
            "executorType", request.getExecutorType(),
            "handler", request.getHandler()
        );
    }

    /**
     * 测试用的 HTTP 任务执行器
     * 模拟一个简单的数据处理任务
     */
    @PostMapping("/task")
    @RequirePermission("task:read")
    public WorkerExecutionResult testTask(@RequestBody WorkerExecuteRequest request) {
        log.info("🚀 Test HTTP endpoint called. Instance: {}, Tenant: {}", 
            request.getInstanceId(), request.getTenantCode());
        
        try {
            // 模拟一些处理时间
            TimeUnit.MILLISECONDS.sleep(100);
            
            // 模拟成功执行
            return WorkerExecutionResult.builder()
                .taskId(request.getTaskId())
                .instanceId(request.getInstanceId())
                .status("SUCCESS")
                .message("Test HTTP task executed successfully! 🎉")
                .attempt(request.getAttempt())
                .durationMillis(100)
                .build();
                
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WorkerExecutionResult.builder()
                .taskId(request.getTaskId())
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Task interrupted: " + e.getMessage())
                .attempt(request.getAttempt())
                .build();
        }
    }

    /**
     * 测试用的失败任务执行器
     * 模拟任务执行失败的情况
     */
    @PostMapping("/task-fail")
    @RequirePermission("task:read")
    public WorkerExecutionResult testTaskFail(@RequestBody WorkerExecuteRequest request) {
        log.warn("❌ Test HTTP endpoint called, simulating failure. Instance: {}, Tenant: {}", 
            request.getInstanceId(), request.getTenantCode());
        
        return WorkerExecutionResult.builder()
            .taskId(request.getTaskId())
            .instanceId(request.getInstanceId())
            .status("FAILED")
            .message("Test HTTP task failed intentionally for testing retry mechanism")
            .attempt(request.getAttempt())
            .durationMillis(50)
            .build();
    }

    /**
     * 测试用的慢任务执行器
     * 模拟长时间运行的任务
     */
    @PostMapping("/task-slow")
    @RequirePermission("task:read")
    public WorkerExecutionResult testTaskSlow(@RequestBody WorkerExecuteRequest request) throws InterruptedException {
        log.info("⏳ Test HTTP endpoint called, simulating slow execution. Instance: {}, Tenant: {}", 
            request.getInstanceId(), request.getTenantCode());
        
        // 模拟 5 秒的工作
        TimeUnit.SECONDS.sleep(5);
        
        return WorkerExecutionResult.builder()
            .taskId(request.getTaskId())
            .instanceId(request.getInstanceId())
            .status("SUCCESS")
            .message("Test HTTP task completed after 5 seconds")
            .attempt(request.getAttempt())
            .durationMillis(5000)
            .build();
    }

    /**
     * 测试用的带参数任务执行器
     * 模拟处理自定义参数的任务
     */
    @PostMapping("/task-with-params")
    @RequirePermission("task:read")
    public WorkerExecutionResult testTaskWithParams(@RequestBody WorkerExecuteRequest request) {
        log.info("📝 Test HTTP endpoint called with params: {}. Instance: {}, Tenant: {}", 
            request.getParameters(), request.getInstanceId(), request.getTenantCode());
        
        // 处理参数
        String message = "Received params: " + request.getParameters();
        if (request.getParameters() != null) {
            Object testParam = request.getParameters().get("testParam");
            if (testParam != null) {
                message += " | TestParam: " + testParam;
            }
        }
        
        return WorkerExecutionResult.builder()
            .taskId(request.getTaskId())
            .instanceId(request.getInstanceId())
            .status("SUCCESS")
            .message(message)
            .attempt(request.getAttempt())
            .durationMillis(80)
            .build();
    }
}
