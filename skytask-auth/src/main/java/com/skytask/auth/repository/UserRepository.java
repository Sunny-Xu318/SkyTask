package com.skytask.auth.repository;

import com.skytask.auth.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByTenant_CodeAndUsername(String tenantCode, String username);

    Optional<UserEntity> findByIdAndTenant_Code(Long id, String tenantCode);
}
