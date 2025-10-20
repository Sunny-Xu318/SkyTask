package com.skytask.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HeartbeatResponse {
    String nodeId;
    String name;
    String latest;
    double avgLatency;
    String lastAlert;
    List<Map<String, Object>> logs;
}
