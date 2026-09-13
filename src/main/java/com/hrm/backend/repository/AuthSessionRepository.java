package com.hrm.backend.repository;

import com.hrm.backend.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}
