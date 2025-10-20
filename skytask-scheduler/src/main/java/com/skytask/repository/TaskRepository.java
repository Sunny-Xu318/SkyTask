package com.skytask.repository;

import com.skytask.entity.TaskEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<TaskEntity, Long>, JpaSpecificationExecutor<TaskEntity> {

    boolean existsByTenantIdAndName(Long tenantId, String name);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndEnabled(Long tenantId, Boolean enabled);

    Optional<TaskEntity> findByTenantIdAndId(Long tenantId, Long id);

    Optional<TaskEntity> findByTenantIdAndName(Long tenantId, String name);

    List<TaskEntity> findAllByTenantId(Long tenantId);
}
