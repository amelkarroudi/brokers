package com.brokers.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
