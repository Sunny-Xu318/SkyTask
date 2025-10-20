package com.skytask.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "biz_group", length = 128)
    private String bizGroup;

    @Column(length = 1024)
    private String description;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 512)
    private String handler;

    @Column(name = "cron_expr", length = 128)
    private String cronExpr;

    @Column(name = "timezone", length = 64)
    private String timeZone;

    @Column(name = "shard_count")
    private Integer shardCount;

    @Column(name = "retry_max")
    private Integer retryMax;

    @Column(name = "retry_backoff_ms")
    private Integer retryBackoffMs;

    @Column(name = "concurrency_policy", length = 32)
    private String concurrencyPolicy;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
