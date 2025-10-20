package com.skytask.dto;

import lombok.Data;

@Data
public class TaskTriggerRequest {
    private boolean manual;
    private String operator;
    private String shardingKey;
    private String payload;
}
