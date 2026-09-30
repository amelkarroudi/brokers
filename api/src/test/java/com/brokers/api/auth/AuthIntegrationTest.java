package com.brokers.api.auth;

import com.brokers.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void signupCreatesOrganizationAndReturnsWorkingToken() throws Exception {
        Tenant tenant = signup();

        Response me = get("/api/auth/me", tenant.token()).expectStatus(200);

        assertThat(tenant.token()).startsWith("brk_");
        assertThat(me.body().at("/member/email").asString()).isEqualTo(tenant.email());
        assertThat(me.body().at("/member/role").asString()).isEqualTo("OWNER");
        assertThat(me.body().at("/organization/name").asString()).isEqualTo("Atlas Cars");
        assertThat(me.body().at("/organization/defaultCurrency").asString()).isEqualTo("EUR");
        assertThat(me.body().at("/organization/slug").asString()).startsWith("atlas-cars");
    }

    @Test
    void emailsAreUniqueIgnoringCase() throws Exception {
        Tenant tenant = signup();

        Response duplicate = post("/api/auth/signup", null, Map.of(
                "organizationName", "Other", "fullName", "Someone",
                "email", tenant.email().toUpperCase(), "password", PASSWORD));

        duplicate.expectStatus(409);
        assertThat(duplicate.code()).isEqualTo("email_taken");
    }

    @Test
    void rejectsWeakPasswordsAndInvalidInput() throws Exception {
        Response response = post("/api/auth/signup", null, Map.of(
                "organizationName", "Weak", "fullName", "Someone",
                "email", "not-an-email", "password", "short"));

        response.expectStatus(400);
        assertThat(response.code()).isEqualTo("validation_failed");
        assertThat(response.body().at("/errors/password").asString()).isNotBlank();
        assertThat(response.body().at("/errors/email").asString()).isNotBlank();
    }

    @Test
    void rejectsUnknownCurrency() throws Exception {
        Response response = post("/api/auth/signup", null, Map.of(
                "organizationName", "Acme", "fullName", "Someone",
                "email", unique("x") + "@example.com", "password", PASSWORD, "defaultCurrency", "XYZ"));

        response.expectStatus(422);
        assertThat(response.code()).isEqualTo("invalid_currency");
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        Tenant tenant = signup();

        Response wrongPassword = post("/api/auth/login", null, Map.of("email", tenant.email(), "password", "Wrong-pass-1"));
        Response unknownEmail = post("/api/auth/login", null, Map.of("email", "nobody@example.com", "password", "Wrong-pass-1"));

        wrongPassword.expectStatus(401);
        unknownEmail.expectStatus(401);
        assertThat(wrongPassword.body().get("detail")).isEqualTo(unknownEmail.body().get("detail"));
        assertThat(wrongPassword.code()).isEqualTo("invalid_credentials");
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        Tenant tenant = signup();

        Response login = post("/api/auth/login", null, Map.of("email", tenant.email().toUpperCase(), "password", PASSWORD));

        login.expectStatus(200);
        assertThat(login.body().get("token").asString()).isNotEqualTo(tenant.token());
    }

    @Test
    void locksAccountAfterRepeatedFailures() throws Exception {
        Tenant tenant = signup();
        for (int attempt = 0; attempt < 5; attempt++) {
            post("/api/auth/login", null, Map.of("email", tenant.email(), "password", "Wrong-pass-1")).expectStatus(401);
        }

        Response locked = post("/api/auth/login", null, Map.of("email", tenant.email(), "password", PASSWORD));

        locked.expectStatus(423);
        assertThat(locked.code()).isEqualTo("account_locked");
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        Tenant tenant = signup();

        post("/api/auth/logout", tenant.token(), null).expectStatus(204);

        get("/api/auth/me", tenant.token()).expectStatus(401);
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        Response missing = get("/api/vehicles", null);
        Response forged = get("/api/vehicles", "brk_forged-token");
        Response malformed = get("/api/vehicles", "not-a-token");

        missing.expectStatus(401);
        forged.expectStatus(401);
        malformed.expectStatus(401);
        assertThat(missing.code()).isEqualTo("unauthenticated");
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        Tenant tenant = signup();

        Integer plainMatches = jdbc.queryForObject(
                "select count(*) from auth_sessions where token_hash = ?", Integer.class, tenant.token());

        assertThat(plainMatches).isZero();
    }
}
