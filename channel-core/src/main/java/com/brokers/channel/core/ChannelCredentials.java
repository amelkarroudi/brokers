package com.brokers.channel.core;

import java.util.Objects;

/**
 * Credentials an organization uses to authenticate against a channel.
 *
 * @param accountId the supplier / partner account identifier issued by the channel
 * @param apiKey    the API username or OAuth client id
 * @param apiSecret the API password or OAuth client secret
 */
public record ChannelCredentials(String accountId, String apiKey, String apiSecret) {

    public ChannelCredentials {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(apiSecret, "apiSecret");
    }

    @Override
    public String toString() {
        return "ChannelCredentials[accountId=" + accountId + ", apiKey=****, apiSecret=****]";
    }
}
