package com.skytask.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskMetricsResponse {
    long totalTasks;
    long inactiveTasks;
    double successRate;
    long failedToday;
    long backlog;
    List<Map<String, Object>> trend;
    List<Map<String, Object>> recentEvents;
    List<Map<String, Object>> topFailed;
}
