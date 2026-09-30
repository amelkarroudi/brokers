package com.brokers.api.connection;

import com.brokers.api.common.ApiException;
import com.brokers.api.config.BrokersProperties;
import com.brokers.api.config.ChannelRegistry;
import com.brokers.api.connection.ConnectionDtos.ConnectRequest;
import com.brokers.api.connection.ConnectionDtos.ConnectionSecretView;
import com.brokers.api.connection.ConnectionDtos.ConnectionView;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.api.security.CredentialCipher;
import com.brokers.api.sync.SyncJobScheduler;
import com.brokers.api.sync.SyncTrigger;
import com.brokers.channel.core.Channel;
import com.brokers.channel.core.ChannelCredentials;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Connects organizations to channels. Credentials are checked against the channel before they
 * are stored, so a connection is only ever saved as CONNECTED with credentials that worked.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ChannelConnectionRepository connections;
    private final ChannelListingRepository listings;
    private final ChannelRegistry registry;
    private final CredentialCipher cipher;
    private final SyncJobScheduler syncJobs;
    private final TransactionTemplate transactions;
    private final BrokersProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ConnectionService(ChannelConnectionRepository connections, ChannelListingRepository listings,
                             ChannelRegistry registry, CredentialCipher cipher, SyncJobScheduler syncJobs,
                             TransactionTemplate transactions, BrokersProperties properties, Clock clock) {
        this.connections = connections;
        this.listings = listings;
        this.registry = registry;
        this.cipher = cipher;
        this.syncJobs = syncJobs;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    public List<ConnectionView> list(UUID organizationId) {
        List<ChannelConnection> existing = connections.findByOrganizationId(organizationId);
        return Arrays.stream(Channel.values())
                .map(channel -> existing.stream()
                        .filter(connection -> connection.getChannel() == channel)
                        .findFirst()
                        .map(this::toView)
                        .orElseGet(() -> notConnected(channel)))
                .toList();
    }

    public ConnectionSecretView connect(UUID organizationId, Channel channel, ConnectRequest request) {
        String accountId = request.accountId().trim();
        ChannelCredentials credentials = new ChannelCredentials(accountId, request.apiKey().trim(), request.apiSecret());
        connections.findByChannelAndAccountId(channel, accountId)
                .filter(other -> !other.getOrganizationId().equals(organizationId))
                .ifPresent(other -> {
                    throw ApiException.conflict("account_already_connected",
                            "This " + channel + " account is connected to another organization");
                });

        verifyWithChannel(channel, credentials);

        Connected result = transactions.execute(status -> {
            ChannelConnection connection = connections.findByOrganizationIdAndChannel(organizationId, channel)
                    .orElseGet(() -> new ChannelConnection(organizationId, channel));
            boolean accountChanged = connection.getAccountId() != null && !connection.getAccountId().equals(accountId);
            if (accountChanged) {
                int reset = listings.resetExternalReferences(organizationId, channel);
                log.info("Account changed for {} on {}; reset {} listings", organizationId, channel, reset);
            }

            connection.connect(accountId, cipher.encrypt(credentials.apiKey()), cipher.encrypt(credentials.apiSecret()),
                    clock.instant());
            String issuedSecret = applyWebhookSecret(connection, request.webhookSecret());
            ChannelConnection persisted = connections.save(connection);
            syncJobs.enqueueChannelResync(organizationId, channel, SyncTrigger.CHANNEL_CONNECTED);
            return new Connected(persisted, issuedSecret);
        });
        return new ConnectionSecretView(toView(result.connection()), result.issuedSecret());
    }

    /** Re-checks stored credentials, e.g. after the channel reported them invalid. */
    public ConnectionView verify(UUID organizationId, Channel channel) {
        ChannelConnection connection = find(organizationId, channel);
        if (connection.getStatus() == ConnectionStatus.DISCONNECTED) {
            throw ApiException.conflict("channel_disconnected", channel + " is disconnected; connect it again");
        }

        ChannelErrorKind failure = null;
        String error = null;
        try {
            registry.client(channel).verifyCredentials(credentials(connection));
        } catch (ChannelException e) {
            failure = e.kind();
            error = e.getMessage();
        }
        if (failure != null && failure != ChannelErrorKind.AUTHENTICATION) {
            throw channelUnavailable(channel, error);
        }

        boolean valid = failure == null;
        String finalError = error;
        return transactions.execute(status -> {
            ChannelConnection current = find(organizationId, channel);
            boolean wasInvalid = current.getStatus() == ConnectionStatus.INVALID_CREDENTIALS;
            if (!valid) {
                current.markInvalidCredentials(finalError);
                return toView(current);
            }
            current.markVerified(clock.instant());
            if (wasInvalid) {
                syncJobs.enqueueChannelResync(organizationId, channel, SyncTrigger.CHANNEL_RECONNECTED);
            }
            return toView(current);
        });
    }

    /** Stops syncing and takes every published listing off the channel. */
    public ConnectionView disconnect(UUID organizationId, Channel channel) {
        return transactions.execute(status -> {
            ChannelConnection connection = find(organizationId, channel);
            if (connection.getStatus() == ConnectionStatus.DISCONNECTED) {
                return toView(connection);
            }
            connection.disconnect();
            syncJobs.enqueueChannelDeactivation(organizationId, channel, SyncTrigger.CHANNEL_DISCONNECTED);
            return toView(connection);
        });
    }

    public ConnectionSecretView rotateWebhookSecret(UUID organizationId, Channel channel) {
        String secret = newWebhookSecret();
        ChannelConnection connection = transactions.execute(status -> {
            ChannelConnection current = find(organizationId, channel);
            current.changeWebhookSecret(cipher.encrypt(secret));
            return current;
        });
        return new ConnectionSecretView(toView(connection), secret);
    }

    public ChannelCredentials credentials(ChannelConnection connection) {
        return new ChannelCredentials(connection.getAccountId(),
                cipher.decrypt(connection.getApiKeyEncrypted()),
                cipher.decrypt(connection.getApiSecretEncrypted()));
    }

    public String webhookSecret(ChannelConnection connection) {
        return cipher.decrypt(connection.getWebhookSecretEncrypted());
    }

    /**
     * Uses the caller's secret when given, keeps the existing one on reconnect, and generates one
     * otherwise. Returns the secret only when it was generated, since the caller cannot know it.
     */
    private String applyWebhookSecret(ChannelConnection connection, String requestedSecret) {
        if (requestedSecret != null) {
            connection.changeWebhookSecret(cipher.encrypt(requestedSecret));
            return null;
        }
        if (connection.hasWebhookSecret()) {
            return null;
        }
        String generated = newWebhookSecret();
        connection.changeWebhookSecret(cipher.encrypt(generated));
        return generated;
    }

    private void verifyWithChannel(Channel channel, ChannelCredentials credentials) {
        try {
            registry.client(channel).verifyCredentials(credentials);
        } catch (ChannelException e) {
            if (e.kind() == ChannelErrorKind.AUTHENTICATION) {
                throw ApiException.unprocessable("invalid_channel_credentials",
                        channel + " rejected the credentials. Check the account id, API key and secret.");
            }
            throw channelUnavailable(channel, e.getMessage());
        }
    }

    private ChannelConnection find(UUID organizationId, Channel channel) {
        return connections.findByOrganizationIdAndChannel(organizationId, channel)
                .orElseThrow(() -> ApiException.notFound(channel + " connection"));
    }

    private ConnectionView toView(ChannelConnection connection) {
        return new ConnectionView(
                connection.getChannel(),
                connection.getStatus().name(),
                connection.getId(),
                connection.getAccountId(),
                hint(cipher.decrypt(connection.getApiKeyEncrypted())),
                webhookUrl(connection),
                connection.getLastVerifiedAt(),
                connection.getLastError(),
                connection.getUpdatedAt());
    }

    private static ConnectionView notConnected(Channel channel) {
        return new ConnectionView(channel, "NOT_CONNECTED", null, null, null, null, null, null, null);
    }

    private String webhookUrl(ChannelConnection connection) {
        String base = properties.publicBaseUrl().toString().replaceAll("/+$", "");
        return base + "/api/webhooks/" + connection.getChannel().name().toLowerCase(Locale.ROOT) + "/" + connection.getId();
    }

    private String newWebhookSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hint(String apiKey) {
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    private record Connected(ChannelConnection connection, String issuedSecret) {
    }

    private static ApiException channelUnavailable(Channel channel, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "channel_unavailable",
                channel + " could not be reached to verify the credentials: " + detail);
    }
}
