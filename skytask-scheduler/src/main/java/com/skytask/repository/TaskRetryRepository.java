package com.skytask.repository;

import com.skytask.entity.TaskRetryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRetryRepository extends JpaRepository<TaskRetryEntity, Long> {

    Optional<TaskRetryEntity> findFirstByTenantIdAndInstanceIdAndRetryNoOrderByIdDesc(
            Long tenantId, String instanceId, Integer retryNo);
}
