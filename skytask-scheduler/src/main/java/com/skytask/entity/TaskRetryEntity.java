package com.skytask.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_retry", indexes = { @Index(name = "idx_instance", columnList = "instance_id") })
public class TaskRetryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "instance_id", length = 64, nullable = false)
    private String instanceId;

    @Column(name = "retry_no", nullable = false)
    private Integer retryNo;

    @Column(name = "scheduled_retry_time", nullable = false)
    private LocalDateTime scheduledRetryTime;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
