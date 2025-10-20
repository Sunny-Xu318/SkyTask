package com.skytask.repository;

import com.skytask.entity.TaskInstanceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskInstanceRepository extends JpaRepository<TaskInstanceEntity, Long> {

    Page<TaskInstanceEntity> findByTenantIdAndTaskIdOrderByScheduledTimeDesc(Long tenantId, Long taskId, Pageable pageable);

    Optional<TaskInstanceEntity> findFirstByTenantIdAndTaskIdOrderByScheduledTimeDesc(Long tenantId, Long taskId);

    Optional<TaskInstanceEntity> findByTenantIdAndInstanceId(Long tenantId, String instanceId);

    long countByTenantIdAndStatus(Long tenantId, String status);

    long countByTenantIdAndStatusAndScheduledTimeAfter(Long tenantId, String status, java.time.LocalDateTime after);

    long countByTenantIdAndTaskIdAndStatus(Long tenantId, Long taskId, String status);

    List<TaskInstanceEntity> findTop10ByTenantIdOrderByScheduledTimeDesc(Long tenantId);

    org.springframework.data.domain.Page<TaskInstanceEntity> findByTenantIdAndTaskIdAndScheduledTimeGreaterThanEqualOrderByScheduledTimeDesc(
            Long tenantId, Long taskId, java.time.LocalDateTime scheduledTime, org.springframework.data.domain.Pageable pageable);
}
