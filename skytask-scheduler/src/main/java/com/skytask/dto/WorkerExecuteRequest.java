package com.skytask.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkerExecuteRequest {

    private String instanceId;
    private String operator;
    private Map<String, Object> parameters;
    private int timeoutSeconds;
    private int attempt;
}
