package com.skytask.executor;

import com.skytask.model.ExecutorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 任务执行器工厂
 * 根据执行器类型获取对应的执行器实例
 */
@Component
@Slf4j
public class TaskExecutorFactory implements InitializingBean {

    private final ApplicationContext applicationContext;
    private final Map<ExecutorType, TaskExecutor> executors = new EnumMap<>(ExecutorType.class);

    public TaskExecutorFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        // 注册 HTTP 执行器
        HttpTaskExecutor httpExecutor = applicationContext.getBean(HttpTaskExecutor.class);
        executors.put(ExecutorType.HTTP, httpExecutor);
        log.info("Registered HTTP task executor");

        // 注册 Spring Bean 执行器
        SpringBeanTaskExecutor springBeanExecutor = applicationContext.getBean(SpringBeanTaskExecutor.class);
        executors.put(ExecutorType.SPRING_BEAN, springBeanExecutor);
        log.info("Registered Spring Bean task executor");

        // 注册 Shell 任务执行器
        ShellTaskExecutor shellExecutor = applicationContext.getBean(ShellTaskExecutor.class);
        executors.put(ExecutorType.SHELL, shellExecutor);
        log.info("Registered Shell task executor");

        log.info("Task executor factory initialized with {} executors", executors.size());
    }

    /**
     * 根据执行器类型和处理器获取执行器
     * 
     * @param executorType 执行器类型
     * @param handler 处理器地址/类名/脚本路径
     * @return 任务执行器
     * @throws IllegalArgumentException 如果找不到对应的执行器或处理器不支持
     */
    public TaskExecutor getExecutor(ExecutorType executorType, String handler) {
        TaskExecutor executor = executors.get(executorType);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for type: " + executorType);
        }
        if (!executor.supports(handler)) {
            throw new IllegalArgumentException(
                    String.format("Handler '%s' is not supported by executor type '%s'", handler, executorType));
        }
        return executor;
    }

    /**
     * 根据执行器类型获取执行器（不检查处理器支持）
     * 
     * @param executorType 执行器类型
     * @return 任务执行器
     * @throws IllegalArgumentException 如果找不到对应的执行器
     */
    public TaskExecutor getExecutor(ExecutorType executorType) {
        TaskExecutor executor = executors.get(executorType);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for type: " + executorType);
        }
        return executor;
    }
}


