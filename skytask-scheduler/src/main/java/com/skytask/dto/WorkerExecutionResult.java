package com.skytask.dto;

import lombok.Data;

@Data
public class WorkerExecutionResult {

    private String taskId;
    private String instanceId;
    private String status;
    private String message;
    private long durationMillis;
    private int attempt;
}
