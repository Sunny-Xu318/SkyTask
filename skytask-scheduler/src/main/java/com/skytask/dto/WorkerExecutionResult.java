package com.skytask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerExecutionResult {

    private String taskId;
    private String instanceId;
    private String status;
    private String message;
    private long durationMillis;
    private int attempt;
}
