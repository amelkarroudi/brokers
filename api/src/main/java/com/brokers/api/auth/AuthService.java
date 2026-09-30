package com.brokers.api.auth;

import com.brokers.api.auth.AuthDtos.ClientInfo;
import com.brokers.api.auth.AuthDtos.LoginRequest;
import com.brokers.api.auth.AuthDtos.MeResponse;
import com.brokers.api.auth.AuthDtos.SessionResponse;
import com.brokers.api.auth.AuthDtos.SignupRequest;
import com.brokers.api.common.ApiException;
import com.brokers.api.common.Hashing;
import com.brokers.api.config.BrokersProperties;
import com.brokers.api.organization.Organization;
import com.brokers.api.organization.OrganizationMember;
import com.brokers.api.organization.OrganizationMemberRepository;
import com.brokers.api.organization.OrganizationRepository;
import com.brokers.api.security.AuthenticatedMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String TOKEN_PREFIX = "brk_";
    /** BCrypt only uses the first 72 bytes of a password. */
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String DEFAULT_CURRENCY = "EUR";
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final BrokersProperties.Security settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Compared against when the email is unknown so both paths cost the same time. */
    private final String dummyHash;

    public AuthService(OrganizationRepository organizations, OrganizationMemberRepository members,
                       AuthSessionRepository sessions, PasswordEncoder passwordEncoder,
                       BrokersProperties properties, Clock clock) {
        this.organizations = organizations;
        this.members = members;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.settings = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public SessionResponse signup(SignupRequest request, ClientInfo client) {
        String email = normalizeEmail(request.email());
        if (members.existsByEmail(email)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.unprocessable("password_too_long", "Password must be at most 72 bytes");
        }

        Organization organization = organizations.save(new Organization(
                request.organizationName().trim(),
                uniqueSlug(request.organizationName()),
                currencyOrDefault(request.defaultCurrency()),
                timezoneOrDefault(request.timezone())));
        OrganizationMember member = members.save(new OrganizationMember(
                organization.getId(), email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        member.recordSuccessfulLogin(clock.instant());

        log.info("Organization {} signed up", organization.getId());
        return openSession(member, organization, client);
    }

    /**
     * Authenticates with email and password. Failures are deliberately indistinguishable (same
     * message, same timing) so the endpoint cannot be used to discover registered emails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionResponse login(LoginRequest request, ClientInfo client) {
        Instant now = clock.instant();
        Optional<OrganizationMember> found = members.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }

        OrganizationMember member = found.get();
        if (member.isLockedAt(now)) {
            throw new ApiException(HttpStatus.LOCKED, "account_locked",
                    "Too many failed attempts. Try again after " + member.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            member.recordFailedLogin(now, settings.maxFailedLogins(), settings.lockoutDuration());
            throw invalidCredentials();
        }

        Organization organization = organizations.findById(member.getOrganizationId())
                .orElseThrow(this::invalidCredentials);
        if (!organization.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_suspended", "This organization is suspended");
        }

        member.recordSuccessfulLogin(now);
        return openSession(member, organization, client);
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessions.findById(sessionId).ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedMember principal) {
        OrganizationMember member = members.findById(principal.memberId())
                .orElseThrow(() -> ApiException.notFound("Member"));
        Organization organization = organizations.findById(principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization"));
        return MeResponse.of(member, organization);
    }

    /** Resolves a bearer token to its principal, or empty when it is unknown, expired or revoked. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedMember> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<AuthSession> session = sessions.findByTokenHash(Hashing.sha256Hex(rawToken))
                .filter(candidate -> candidate.isActiveAt(now));
        if (session.isEmpty()) {
            return Optional.empty();
        }

        Optional<OrganizationMember> member = members.findById(session.get().getMemberId());
        if (member.isEmpty()) {
            return Optional.empty();
        }
        boolean organizationActive = organizations.findById(member.get().getOrganizationId())
                .map(Organization::isActive)
                .orElse(false);
        if (!organizationActive) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedMember(
                member.get().getId(), member.get().getOrganizationId(), session.get().getId(), member.get().getEmail()));
    }

    @Transactional
    public int purgeExpiredSessions() {
        return sessions.deleteExpiredBefore(clock.instant());
    }

    private SessionResponse openSession(OrganizationMember member, Organization organization, ClientInfo client) {
        Instant now = clock.instant();
        String token = newToken();
        AuthSession session = sessions.save(new AuthSession(member.getId(), Hashing.sha256Hex(token),
                truncate(client.userAgent(), 255), truncate(client.ipAddress(), 45), now, now.plus(settings.sessionTtl())));
        return new SessionResponse(token, session.getExpiresAt(), MeResponse.of(member, organization));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = "org";
        }
        base = base.length() > 60 ? base.substring(0, 60) : base;

        String slug = base;
        while (organizations.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private static String currencyOrDefault(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        try {
            return Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("invalid_currency", "Unknown currency " + currency);
        }
    }

    private static String timezoneOrDefault(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw ApiException.unprocessable("invalid_timezone", "Unknown timezone " + timezone);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
    }
}
