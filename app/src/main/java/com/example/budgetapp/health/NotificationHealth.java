package com.example.budgetapp.health;

public final class NotificationHealth {
    public enum Connection {UNKNOWN, CONNECTING, CONNECTED, DISCONNECTED}

    public final boolean authorized;
    public final Connection connection;
    public final Long lastConnectedAtMs, lastEventAtMs, lastParsedAtMs;
    public final int pendingCount;
    public final String errorCode;

    public NotificationHealth(boolean authorized, Connection connection, Long connected, Long event, Long parsed, int pending, String error) {
        this.authorized = authorized;
        this.connection = connection;
        lastConnectedAtMs = connected;
        lastEventAtMs = event;
        lastParsedAtMs = parsed;
        pendingCount = pending;
        errorCode = error;
    }
}
