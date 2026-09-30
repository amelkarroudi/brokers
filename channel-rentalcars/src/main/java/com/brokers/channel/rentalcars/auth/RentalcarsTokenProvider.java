package com.brokers.channel.rentalcars.auth;

import com.brokers.channel.core.Channel;
import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import com.brokers.channel.core.http.ChannelHttp;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtains OAuth2 client-credentials tokens and caches them per client id until shortly before
 * they expire, so each organization authenticates once per token lifetime instead of per call.
 */
public class RentalcarsTokenProvider {

    /** Refresh this long before expiry so a token never expires mid-request. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public RentalcarsTokenProvider(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public String accessToken(ChannelCredentials credentials) {
        Instant now = clock.instant();
        return cache.compute(cacheKey(credentials), (key, current) -> current != null && current.validAt(now)
                ? current
                : fetch(credentials)).value();
    }

    /** Drops a cached token, e.g. after the API rejected it. */
    public void invalidate(ChannelCredentials credentials) {
        cache.remove(cacheKey(credentials));
    }

    private CachedToken fetch(ChannelCredentials credentials) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", credentials.apiKey());
        form.add("client_secret", credentials.apiSecret());
        form.add("scope", "supplier:" + credentials.accountId());

        RcTokenResponse response = ChannelHttp.call(Channel.RENTALCARS, "obtain access token", () -> restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(RcTokenResponse.class));

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ChannelException(Channel.RENTALCARS, ChannelErrorKind.AUTHENTICATION, "token endpoint returned no access_token");
        }
        Instant expiresAt = clock.instant().plusSeconds(response.expiresIn()).minus(EXPIRY_SKEW);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    /** Key includes the secret so rotated credentials never reuse a token issued for the old ones. */
    private static String cacheKey(ChannelCredentials credentials) {
        return credentials.accountId() + '\u0000' + credentials.apiKey() + '\u0000' + credentials.apiSecret().hashCode();
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean validAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
