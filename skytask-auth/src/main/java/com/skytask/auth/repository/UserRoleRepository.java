package com.skytask.auth.repository;

import com.skytask.auth.entity.UserRoleEntity;
import com.skytask.auth.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {

    List<UserRoleEntity> findByUser_Id(Long userId);
}
