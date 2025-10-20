package com.skytask.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class WorkerClientFallbackFactory implements FallbackFactory<WorkerClient> {

    @Override
    public WorkerClient create(Throwable cause) {
        return (taskId, request, tenantCode) -> {
            String message = "Worker service unavailable";
            if (cause != null && StringUtils.hasText(cause.getMessage())) {
                message = message + ": " + cause.getMessage();
            }
            log.error(
                    "Worker service unavailable for task {} instance {}: {}",
                    taskId,
                    request.getInstanceId(),
                    message);
            throw new IllegalStateException(message, cause);
        };
    }
}
