package com.skytask.model;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TaskExecutionRecord {
    private String id;
    private String taskId;
    private OffsetDateTime triggerTime;
    private String node;
    private TaskStatus status;
    private long duration;
    private int retry;
    private String log;
    private String traceId;
}
