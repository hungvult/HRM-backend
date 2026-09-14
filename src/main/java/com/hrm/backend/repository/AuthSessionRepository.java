package com.hrm.backend.repository;

import com.hrm.backend.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

import java.util.Optional;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("UPDATE AuthSession s SET s.revokedAt = :now, s.revokeReason = :reason WHERE s.account.id = :accountId AND s.revokedAt IS NULL")
    int revokeActiveByAccountId(@Param("accountId") Long accountId, @Param("now") OffsetDateTime now, @Param("reason") String reason);
}
