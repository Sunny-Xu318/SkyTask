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
@Table(
        name = "task_instance",
        indexes = {
            @Index(name = "idx_task_status", columnList = "task_id,status"),
            @Index(name = "idx_sched_time", columnList = "scheduled_time")
        })
public class TaskInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "instance_id", nullable = false, length = 64)
    private String instanceId;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "triggered_by", length = 32)
    private String triggeredBy;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "executor_node_id")
    private Long executorNodeId;

    @Column(name = "shard_index")
    private Integer shardIndex;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "result", columnDefinition = "text")
    private String result;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
