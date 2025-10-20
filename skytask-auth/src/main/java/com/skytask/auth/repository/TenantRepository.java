package com.skytask.auth.repository;

import com.skytask.auth.entity.TenantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByCode(String code);
}
