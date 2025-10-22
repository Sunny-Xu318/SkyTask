package com.skytask.executor;

import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Spring Bean 任务执行器
 * 调用 Spring 容器中的 Bean 来执行任务
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpringBeanTaskExecutor implements TaskExecutor {
    
    private final ApplicationContext applicationContext;
    
    @Override
    public WorkerExecutionResult execute(String handler, WorkerExecuteRequest request, String tenantCode) {
        if (!StringUtils.hasText(handler)) {
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Spring Bean handler cannot be empty")
                .attempt(request.getAttempt())
                .build();
        }
        
        log.info("Executing Spring Bean task: {} for instance {}", handler, request.getInstanceId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 解析 handler：支持 "beanName" 或 "beanName.methodName" 格式
            String beanName;
            String methodName = "execute";
            
            if (handler.contains(".")) {
                int lastDotIndex = handler.lastIndexOf('.');
                String possibleMethod = handler.substring(lastDotIndex + 1);
                
                // 判断是类名还是方法名
                if (possibleMethod.matches("[a-z][a-zA-Z0-9]*")) {
                    // 小写开头，可能是方法名
                    beanName = handler.substring(0, lastDotIndex);
                    methodName = possibleMethod;
                } else {
                    // 大写开头，可能是类名
                    beanName = handler;
                }
            } else {
                beanName = handler;
            }
            
            // 获取 Bean
            Object bean = getBean(beanName);
            if (bean == null) {
                throw new IllegalStateException("Bean not found: " + beanName);
            }
            
            // 调用方法
            Object result = invokeMethod(bean, methodName, request);
            long duration = System.currentTimeMillis() - startTime;
            
            // 解析结果
            String status = "SUCCESS";
            String message = "Spring Bean execution completed";
            
            if (result instanceof WorkerExecutionResult) {
                return (WorkerExecutionResult) result;
            } else if (result instanceof String) {
                message = (String) result;
            } else if (result instanceof Boolean) {
                status = (Boolean) result ? "SUCCESS" : "FAILED";
            }
            
            log.info("Spring Bean task executed: {} - {} ({}ms)", handler, status, duration);
            
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status(status)
                .message(message)
                .attempt(request.getAttempt())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to execute Spring Bean task: {} - {}", handler, e.getMessage(), e);
            return WorkerExecutionResult.builder()
                .taskId("")
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message("Spring Bean execution failed: " + e.getMessage())
                .attempt(request.getAttempt())
                .build();
        }
    }
    
    @Override
    public boolean supports(String handler) {
        // 判断是否是 Java 类名格式或 Bean 名称
        return StringUtils.hasText(handler) && 
               (handler.contains(".") && Character.isLowerCase(handler.charAt(0)) || 
                !handler.contains("/") && !handler.contains("http"));
    }
    
    /**
     * 获取 Bean 实例
     */
    private Object getBean(String beanName) {
        try {
            // 尝试按名称获取
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName);
            }
            
            // 尝试按类名获取
            try {
                Class<?> clazz = Class.forName(beanName);
                return applicationContext.getBean(clazz);
            } catch (ClassNotFoundException e) {
                // 尝试首字母小写
                String simpleName = beanName;
                if (beanName.contains(".")) {
                    simpleName = beanName.substring(beanName.lastIndexOf('.') + 1);
                }
                String beanNameLower = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
                if (applicationContext.containsBean(beanNameLower)) {
                    return applicationContext.getBean(beanNameLower);
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to get bean: {}", beanName, e);
            return null;
        }
    }
    
    /**
     * 调用 Bean 方法
     */
    private Object invokeMethod(Object bean, String methodName, WorkerExecuteRequest request) throws Exception {
        Class<?> clazz = bean.getClass();
        
        // 尝试查找方法：优先带参数的
        try {
            Method method = clazz.getMethod(methodName, WorkerExecuteRequest.class);
            return method.invoke(bean, request);
        } catch (NoSuchMethodException e) {
            // 尝试无参方法
            try {
                Method method = clazz.getMethod(methodName);
                return method.invoke(bean);
            } catch (NoSuchMethodException e2) {
                // 尝试通用参数类型
                try {
                    Method method = clazz.getMethod(methodName, Object.class);
                    return method.invoke(bean, request);
                } catch (NoSuchMethodException e3) {
                    throw new NoSuchMethodException(
                        "Method not found: " + methodName + " in " + clazz.getName());
                }
            }
        }
    }
}
