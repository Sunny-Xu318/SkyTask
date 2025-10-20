package com.skytask.service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskFailureEscalationEvent extends ApplicationEvent {

    private final long taskId;
    private final Long tenantId;
    private final String taskName;
    private final double failureRate;
    private final long sampleCount;

    public TaskFailureEscalationEvent(Object source, long taskId, Long tenantId, String taskName, double failureRate, long sampleCount) {
        super(source);
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.taskName = taskName;
        this.failureRate = failureRate;
        this.sampleCount = sampleCount;
    }
}
