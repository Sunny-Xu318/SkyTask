package com.skytask.repository;

import com.skytask.entity.TaskDependencyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependencyEntity, Long> {

    List<TaskDependencyEntity> findByTenantIdAndTaskId(Long tenantId, Long taskId);
}
