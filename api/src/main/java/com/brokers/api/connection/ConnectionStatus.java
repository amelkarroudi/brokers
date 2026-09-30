package com.brokers.api.connection;

public enum ConnectionStatus {
    CONNECTED,
    /** The channel rejected the stored credentials; syncing is paused until they are updated. */
    INVALID_CREDENTIALS,
    DISCONNECTED
}
