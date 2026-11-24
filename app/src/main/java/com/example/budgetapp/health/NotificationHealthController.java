package com.example.budgetapp.health;

import java.time.Clock;

import com.example.budgetapp.health.NotificationHealth.Connection;

public final class NotificationHealthController {
    private final Clock clock;
    private final java.util.function.LongSupplier elapsed;
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    public void addListener(Runnable listener){listeners.addIfAbsent(listener);}
    private void changed(){for(Runnable listener:listeners)listener.run();}
    private boolean authorized;
    private Connection connection = Connection.UNKNOWN;
    private Long connected, event, parsed, lastRebind;
    private int pending;
    private String error;

    public NotificationHealthController(Clock clock, Long nullableLastConnectedAtMs) {
        this(clock,nullableLastConnectedAtMs,()->System.nanoTime()/1000000L);
    }

    public NotificationHealthController(Clock clock,Long nullableLastConnectedAtMs,java.util.function.LongSupplier elapsed){
        this.clock=clock;this.elapsed=elapsed;
        connected = nullableLastConnectedAtMs;
    }

    public synchronized void restoreHistory(Long lastEvent, Long lastParsed) {
        event = lastEvent;
        parsed = lastParsed;
    }

    public synchronized void onAuthorization(boolean authorized) {
        if (this.authorized != authorized) {
            this.authorized = authorized;
            connection = authorized ? Connection.CONNECTING : Connection.DISCONNECTED;
        } else if (!authorized) connection = Connection.DISCONNECTED;
        changed();
    }

    public synchronized void onConnected() {
        connection = Connection.CONNECTED;
        authorized = true;
        connected = clock.millis();
        error = null;
        changed();
    }

    public synchronized void onDisconnected() {
        connection = Connection.DISCONNECTED;
        changed();
    }

    public synchronized void onEvent() {
        event = clock.millis();
        changed();
    }

    public synchronized void onParsed() {
        parsed = clock.millis();
        error = null;
        changed();
    }

    public synchronized void onBacklog(int count, String errorCode) {
        pending = count;
        error = errorCode;
        changed();
    }

    public synchronized boolean shouldRequestRebind() {
        long now = elapsed.getAsLong();
        if (!authorized || connection == Connection.CONNECTED || lastRebind != null && (now < lastRebind || now - lastRebind < 30000))
            return false;
        lastRebind = now;
        return true;
    }

    public synchronized NotificationHealth snapshot() {
        return new NotificationHealth(authorized, connection, connected, event, parsed, pending, error);
    }
}
