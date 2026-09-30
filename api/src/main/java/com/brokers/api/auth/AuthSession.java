package com.brokers.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A login session backed by an opaque bearer token. Only the token's SHA-256 is stored. */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {
    }

    public AuthSession(UUID memberId, String tokenHash, String userAgent, String ipAddress, Instant now, Instant expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt != null) {
            return;
        }
        revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
