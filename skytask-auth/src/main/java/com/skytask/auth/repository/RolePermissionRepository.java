package com.skytask.auth.repository;

import com.skytask.auth.entity.RolePermissionEntity;
import com.skytask.auth.entity.RolePermissionId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, RolePermissionId> {

    List<RolePermissionEntity> findByRole_IdIn(Collection<Long> roleIds);
}
