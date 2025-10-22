package com.skytask.executor;

import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 任务执行器
 * 通过 HTTP POST 调用用户指定的接口
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpTaskExecutor implements TaskExecutor {
    
    private final RestTemplate restTemplate;
    
    @Override
    public WorkerExecutionResult execute(String handler, WorkerExecuteRequest request, String tenantCode) {
        if (!StringUtils.hasText(handler)) {
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("HTTP handler URL cannot be empty")
                .attempt(request.getAttempt())
                .build();
        }
        
        // 确保 URL 格式正确
        String url = handler.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        
        log.info("Executing HTTP task: {} for instance {}", url, request.getInstanceId());
        
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-SkyTask-Tenant", tenantCode);
        headers.set("X-SkyTask-Instance-Id", request.getInstanceId());
        
        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", request.getInstanceId());
        body.put("operator", request.getOperator());
        body.put("parameters", request.getParameters());
        body.put("timeoutSeconds", request.getTimeoutSeconds());
        body.put("attempt", request.getAttempt());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        try {
            // 调用 HTTP 接口
            long startTime = System.currentTimeMillis();
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            long duration = System.currentTimeMillis() - startTime;
            
            // 检查 HTTP 状态码
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("HTTP task returned non-2xx status: {} - {}", url, response.getStatusCode());
                return WorkerExecutionResult.builder()
                    .taskId("")
                    .instanceId(request.getInstanceId())
                    .status("FAILED")
                    .message("HTTP request failed with status: " + response.getStatusCode())
                    .attempt(request.getAttempt())
                    .build();
            }
            
            // 解析响应
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("HTTP response body is null for: {}", url);
                return WorkerExecutionResult.builder()
                    .taskId("")
                    .instanceId(request.getInstanceId())
                    .status("SUCCESS")
                    .message("HTTP execution completed (empty response)")
                    .attempt(request.getAttempt())
                    .build();
            }
            
            // 构建执行结果
            String status = extractString(responseBody, "status", "SUCCESS");
            String message = extractString(responseBody, "message", "HTTP execution completed");
            
            log.info("HTTP task executed successfully: {} - {} ({}ms)", url, status, duration);
            
            return WorkerExecutionResult.builder()
                .taskId(extractString(responseBody, "taskId", ""))
                .instanceId(request.getInstanceId())
                .status(status)
                .message(message)
                .attempt(request.getAttempt())
                .build();
                
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 处理 HTTP 错误状态码（4xx, 5xx）
            log.error("HTTP task failed with status {}: {} - {}", 
                e.getStatusCode(), url, e.getResponseBodyAsString());
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message(String.format("HTTP %s: %s", e.getStatusCode(), e.getStatusText()))
                .attempt(request.getAttempt())
                .build();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 处理网络连接错误
            log.error("HTTP task connection failed: {} - {}", url, e.getMessage());
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Connection failed: " + e.getMessage())
                .attempt(request.getAttempt())
                .build();
        } catch (Exception e) {
            // 处理其他异常
            log.error("Failed to execute HTTP task: {} - {}", url, e.getMessage(), e);
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("HTTP execution failed: " + e.getMessage())
                .attempt(request.getAttempt())
                .build();
        }
    }
    
    @Override
    public boolean supports(String handler) {
        return StringUtils.hasText(handler) && 
               (handler.startsWith("http://") || handler.startsWith("https://") || 
                !handler.contains(":") || handler.contains("."));
    }
    
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
}
