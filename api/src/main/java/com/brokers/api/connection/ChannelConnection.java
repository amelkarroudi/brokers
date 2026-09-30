package com.brokers.api.connection;

import com.brokers.api.common.BaseEntity;
import com.brokers.channel.core.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An organization's account on a channel. Secrets are stored encrypted. */
@Entity
@Table(name = "channel_connections")
public class ChannelConnection extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConnectionStatus status;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "api_key_encrypted", nullable = false, length = 1024)
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", nullable = false, length = 1024)
    private String apiSecretEncrypted;

    @Column(name = "webhook_secret_encrypted", nullable = false, length = 1024)
    private String webhookSecretEncrypted;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected ChannelConnection() {
    }

    public ChannelConnection(UUID organizationId, Channel channel) {
        this.organizationId = organizationId;
        this.channel = channel;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    public void connect(String accountId, String apiKeyEncrypted, String apiSecretEncrypted, Instant verifiedAt) {
        this.accountId = accountId;
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.apiSecretEncrypted = apiSecretEncrypted;
        this.status = ConnectionStatus.CONNECTED;
        this.lastVerifiedAt = verifiedAt;
        this.lastError = null;
    }

    public void changeWebhookSecret(String webhookSecretEncrypted) {
        this.webhookSecretEncrypted = webhookSecretEncrypted;
    }

    public void markVerified(Instant now) {
        status = ConnectionStatus.CONNECTED;
        lastVerifiedAt = now;
        lastError = null;
    }

    public void markInvalidCredentials(String error) {
        status = ConnectionStatus.INVALID_CREDENTIALS;
        lastError = truncate(error);
    }

    public void disconnect() {
        status = ConnectionStatus.DISCONNECTED;
    }

    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }

    public boolean hasWebhookSecret() {
        return webhookSecretEncrypted != null;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Channel getChannel() {
        return channel;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public String getApiSecretEncrypted() {
        return apiSecretEncrypted;
    }

    public String getWebhookSecretEncrypted() {
        return webhookSecretEncrypted;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
