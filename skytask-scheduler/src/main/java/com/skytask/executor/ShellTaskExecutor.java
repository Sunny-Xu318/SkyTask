package com.skytask.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 脚本任务执行器
 * 执行 Shell 脚本或系统命令
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShellTaskExecutor implements TaskExecutor {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public WorkerExecutionResult execute(String handler, WorkerExecuteRequest request, String tenantCode) {
        if (!StringUtils.hasText(handler)) {
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Shell script path cannot be empty")
                .attempt(request.getAttempt())
                .build();
        }
        
        log.info("Executing Shell task: {} for instance {}", handler, request.getInstanceId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 构建命令
            List<String> command = buildCommand(handler, request);
            
            // 创建进程
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            // 设置环境变量
            Map<String, String> env = processBuilder.environment();
            env.put("SKYTASK_INSTANCE_ID", request.getInstanceId());
            env.put("SKYTASK_TENANT", tenantCode);
            env.put("SKYTASK_OPERATOR", request.getOperator() != null ? request.getOperator() : "");
            env.put("SKYTASK_ATTEMPT", String.valueOf(request.getAttempt()));
            
            // 如果有参数，转为 JSON 环境变量
            if (request.getParameters() != null && !request.getParameters().isEmpty()) {
                env.put("SKYTASK_PARAMS", objectMapper.writeValueAsString(request.getParameters()));
            }
            
            // 启动进程
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 10000) {  // 限制输出大小
                        output.append("... (output truncated)");
                        break;
                    }
                }
            }
            
            // 等待执行完成（带超时）
            Integer timeoutSeconds = request.getTimeoutSeconds();
            int timeout = timeoutSeconds != null ? timeoutSeconds : 300;
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Script execution timeout after " + timeout + " seconds");
            }
            
            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - startTime;
            
            String status = exitCode == 0 ? "SUCCESS" : "FAILED";
            String message = exitCode == 0 
                ? "Shell script executed successfully" 
                : "Shell script exited with code " + exitCode;
            
            log.info("Shell task executed: {} - {} (exit code: {}, {}ms)", 
                handler, status, exitCode, duration);
            
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status(status)
                .message(message + "\n\nOutput:\n" + output.toString().trim())
                .attempt(request.getAttempt())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to execute Shell task: {} - {}", handler, e.getMessage(), e);
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Shell execution failed: " + e.getMessage())
                .attempt(request.getAttempt())
                .build();
        }
    }
    
    @Override
    public boolean supports(String handler) {
        return StringUtils.hasText(handler) && 
               (handler.endsWith(".sh") || handler.endsWith(".bash") || 
                handler.endsWith(".py") || handler.endsWith(".pl") ||
                handler.startsWith("/") || handler.contains("/bin/"));
    }
    
    /**
     * 构建执行命令
     */
    private List<String> buildCommand(String handler, WorkerExecuteRequest request) {
        List<String> command = new ArrayList<>();
        
        // 检查脚本文件是否存在
        File scriptFile = new File(handler);
        if (!scriptFile.exists()) {
            throw new IllegalArgumentException("Script file not found: " + handler);
        }
        
        // 检查是否可执行
        if (!scriptFile.canExecute()) {
            log.warn("Script file is not executable, trying to add execute permission: {}", handler);
            scriptFile.setExecutable(true);
        }
        
        // 根据文件类型选择解释器
        if (handler.endsWith(".sh") || handler.endsWith(".bash")) {
            command.add("/bin/bash");
        } else if (handler.endsWith(".py")) {
            command.add("python");
        } else if (handler.endsWith(".pl")) {
            command.add("perl");
        }
        
        command.add(handler);
        
        // 可以添加参数（从 request.parameters 中提取）
        if (request.getParameters() != null) {
            Object args = request.getParameters().get("args");
            if (args instanceof List) {
                for (Object arg : (List<?>) args) {
                    command.add(String.valueOf(arg));
                }
            }
        }
        
        return command;
    }
}
