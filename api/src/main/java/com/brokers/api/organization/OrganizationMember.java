package com.brokers.api.organization;

import com.brokers.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** The login of an organization. Each organization currently has exactly one member. */
@Entity
@Table(name = "organization_members")
public class OrganizationMember extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected OrganizationMember() {
    }

    public OrganizationMember(UUID organizationId, String email, String passwordHash, String fullName) {
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = MemberRole.OWNER;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed attempt and locks the account once the limit is reached. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockout) {
        failedLoginAttempts++;
        if (failedLoginAttempts < maxAttempts) {
            return;
        }
        lockedUntil = now.plus(lockout);
        failedLoginAttempts = 0;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public MemberRole getRole() {
        return role;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
