package com.skytask.model;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AlertRule {
    private String id;
    private String name;
    private String metric;
    private double threshold;
    private List<String> channels;
    private List<String> subscribers;
    private boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
