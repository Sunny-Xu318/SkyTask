package com.skytask.model;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class NodeInfo {
    private String id;
    private String name;
    private String cluster;
    private String host;
    private NodeStatus status;
    private int cpu;
    private int memory;
    private int runningTasks;
    private int backlog;
    private long delay;
    private OffsetDateTime registerTime;
    private String alertLevel;
    private OffsetDateTime lastHeartbeat;
    private int shardCount;
}
