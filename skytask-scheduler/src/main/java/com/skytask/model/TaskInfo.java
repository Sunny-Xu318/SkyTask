package com.skytask.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TaskInfo {
    private String id;
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String name;
    private String group;
    private TaskType type;
    private ExecutorType executorType;
    private String handler;
    private String cronExpr;
    private String timeZone;
    private RouteStrategy routeStrategy;
    private RetryPolicy retryPolicy;
    private int maxRetry;
    private int timeout;
    private String owner;
    private List<String> tags;
    private String idempotentKey;
    private Map<String, Object> parameters;
    private String description;
    private TaskStatus status;
    private boolean enabled;
    private boolean alertEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastTrigger;
    private String lastNode;
    private List<TaskDependency> dependencies;
}
