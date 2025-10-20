package com.skytask.auth.repository;

import com.skytask.auth.entity.UserTokenEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserTokenEntity, Long> {

    Optional<UserTokenEntity> findByRefreshToken(String refreshToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTokenEntity t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTokenEntity t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
