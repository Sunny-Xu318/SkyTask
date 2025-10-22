package com.skytask.executor;

import com.skytask.dto.WorkerExecuteRequest;
import com.skytask.dto.WorkerExecutionResult;

/**
 * 任务执行器接口
 * 不同的执行方式需要实现此接口
 */
public interface TaskExecutor {
    
    /**
     * 执行任务
     * 
     * @param handler 执行器地址/类名/脚本路径
     * @param request 执行请求参数
     * @param tenantCode 租户代码
     * @return 执行结果（不应抛出异常，所有错误都应包装在结果中）
     */
    WorkerExecutionResult execute(String handler, WorkerExecuteRequest request, String tenantCode);
    
    /**
     * 判断是否支持该类型的执行
     * 
     * @param handler 执行器地址
     * @return 是否支持
     */
    boolean supports(String handler);
}


