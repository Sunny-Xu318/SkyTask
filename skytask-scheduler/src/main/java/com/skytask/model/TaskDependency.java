package com.skytask.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskDependency {
    private String id;
    private String name;
    private String triggerType;
    private TaskStatus status;
    private String node;
    private String cronExpr;
}
