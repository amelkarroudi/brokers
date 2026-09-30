package com.brokers.api.auth;

import com.brokers.api.organization.Organization;
import com.brokers.api.organization.OrganizationMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Size(max = 160) String organizationName,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank
            @Size(min = 10, max = 64, message = "must be between 10 and 64 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 currency code") String defaultCurrency,
            @Size(max = 64) String timezone) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {
    }

    public record SessionResponse(String token, Instant expiresAt, MeResponse me) {
    }

    public record MeResponse(MemberView member, OrganizationView organization) {

        static MeResponse of(OrganizationMember member, Organization organization) {
            return new MeResponse(
                    new MemberView(member.getId(), member.getEmail(), member.getFullName(), member.getRole().name(),
                            member.getLastLoginAt()),
                    new OrganizationView(organization.getId(), organization.getName(), organization.getSlug(),
                            organization.getDefaultCurrency(), organization.getTimezone(), organization.getStatus().name()));
        }
    }

    public record MemberView(UUID id, String email, String fullName, String role, Instant lastLoginAt) {
    }

    public record OrganizationView(UUID id, String name, String slug, String defaultCurrency, String timezone, String status) {
    }

    /** Where a login came from, recorded on the session for auditing. */
    public record ClientInfo(String userAgent, String ipAddress) {
    }
}
