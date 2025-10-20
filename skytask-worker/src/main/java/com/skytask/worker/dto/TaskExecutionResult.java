package com.skytask.worker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskExecutionResult {

    private String taskId;
    private String instanceId;
    private String status;
    private String message;
    private long durationMillis;
    private int attempt;
}
