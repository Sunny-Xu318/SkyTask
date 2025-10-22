package com.skytask.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TaskExecutionRecord {
    private String id;
    private String taskId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime triggerTime;
    
    private String node;
    private TaskStatus status;
    private long duration;
    private int retry;
    private String log;
    private String traceId;
    private java.util.Map<String, Object> parameters;
}
