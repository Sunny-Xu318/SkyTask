package com.skytask.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NodeMetricsResponse {
    long totalNodes;
    long onlineNodes;
    long offlineNodes;
    double avgCpu;
    double avgMemory;
}
