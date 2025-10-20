package com.skytask.worker.dto;

import java.util.Map;
import lombok.Data;

@Data
public class TaskExecutionPayload {

    private String instanceId;
    private String operator;
    private Map<String, Object> parameters;
    private int attempt;
    private int timeoutSeconds;
}
