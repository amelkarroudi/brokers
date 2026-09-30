package com.brokers.api.security;

import java.util.UUID;

/** Principal of an authenticated request. Every tenant-scoped query uses {@link #organizationId()}. */
public record AuthenticatedMember(UUID memberId, UUID organizationId, UUID sessionId, String email) {
}
