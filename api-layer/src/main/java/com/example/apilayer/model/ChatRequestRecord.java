package com.example.apilayer.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory record of a submitted chat request, keyed by requestId in
 * {@code ChatRequestRegistry}. Tracks the single-use SSE ticket issued for
 * it.
 */
public class ChatRequestRecord {

    private final String requestId;
    private final String username;
    private final String message;
    private final String ticket;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final AtomicBoolean used = new AtomicBoolean(false);

    public ChatRequestRecord(String requestId, String username, String message,
                              String ticket, Instant createdAt, Instant expiresAt) {
        this.requestId = requestId;
        this.username = username;
        this.message = message;
        this.ticket = ticket;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }

    public String getTicket() {
        return ticket;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Atomically marks the ticket used. Returns true only on the first call
     * (i.e. this is the call that "wins" and may proceed); subsequent calls
     * return false, enforcing single-use.
     */
    public boolean markUsedIfNotAlready() {
        return used.compareAndSet(false, true);
    }

    public boolean isUsed() {
        return used.get();
    }
}
