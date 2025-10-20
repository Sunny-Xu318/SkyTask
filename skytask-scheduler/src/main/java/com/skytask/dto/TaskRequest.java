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

    @NotBlank(message = "????????")
    private String name;

    @NotBlank(message = "????????")
    private String group;

    @NotNull(message = "????????")
    private TaskType type;

    @NotNull(message = "????????")
    private ExecutorType executorType;

    private String cronExpr;
    private String timeZone;

    @NotNull(message = "????????")
    private RouteStrategy routeStrategy;

    @NotNull(message = "????????")
    private RetryPolicy retryPolicy;

    private int maxRetry = 0;
    private int timeout = 60;

    @NotBlank(message = "???????")
    private String owner;

    private List<String> tags;
    private String idempotentKey;
    private Map<String, Object> parameters;
    private String description;
    private boolean alertEnabled;
}



