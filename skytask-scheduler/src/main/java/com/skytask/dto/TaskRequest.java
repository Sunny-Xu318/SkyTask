package com.skytask.dto;

import com.skytask.model.ExecutorType;
import com.skytask.model.RetryPolicy;
import com.skytask.model.RouteStrategy;
import com.skytask.model.TaskType;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskRequest {
    private String id;

    @NotBlank(message = "Task name cannot be empty")
    private String name;

    @NotBlank(message = "Task group cannot be empty")
    private String group;

    @NotNull(message = "Task type cannot be null")
    private TaskType type;

    @NotNull(message = "Executor type cannot be null")
    private ExecutorType executorType;

    @NotBlank(message = "Handler cannot be empty")
    private String handler;

    private String cronExpr;
    private String timeZone;

    @NotNull(message = "Route strategy cannot be null")
    private RouteStrategy routeStrategy;

    @NotNull(message = "Retry policy cannot be null")
    private RetryPolicy retryPolicy;

    private int maxRetry = 0;
    private int timeout = 60;

    @NotBlank(message = "Owner cannot be empty")
    private String owner;

    private List<String> tags;
    private String idempotentKey;
    private Map<String, Object> parameters;
    private String description;
    private boolean alertEnabled;
}



