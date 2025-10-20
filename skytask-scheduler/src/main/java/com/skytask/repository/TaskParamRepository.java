package com.skytask.repository;

import com.skytask.entity.TaskParamEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskParamRepository extends JpaRepository<TaskParamEntity, Long> {

    List<TaskParamEntity> findByTenantIdAndTaskId(Long tenantId, Long taskId);

    void deleteByTenantIdAndTaskId(Long tenantId, Long taskId);
}
