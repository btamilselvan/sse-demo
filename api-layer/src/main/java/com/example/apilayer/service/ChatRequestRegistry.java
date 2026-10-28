package com.example.apilayer.service;

import com.example.apilayer.model.ChatRequestRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of pending chat requests and their single-use SSE
 * tickets. No database -- a simple ConcurrentHashMap keyed by requestId,
 * with a periodic sweep to clean up expired/used entries so this never
 * grows unbounded.
 */
@Component
public class ChatRequestRegistry {

    private final Map<String, ChatRequestRecord> records = new ConcurrentHashMap<>();
    private final long ticketTtlMs;

    public ChatRequestRegistry(@Value("${app.ticket.ttl-ms}") long ticketTtlMs) {
        this.ticketTtlMs = ticketTtlMs;
    }

    public ChatRequestRecord register(String requestId, String username, String message) {
        String ticket = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(ticketTtlMs);
        ChatRequestRecord record = new ChatRequestRecord(requestId, username, message, ticket, now, expiresAt);
        records.put(requestId, record);
        return record;
    }

    public ChatRequestRecord get(String requestId) {
        return records.get(requestId);
    }

    public void remove(String requestId) {
        records.remove(requestId);
    }

    /**
     * Validates the ticket for a requestId and, if valid, atomically marks
     * it used in one step so concurrent/duplicate requests cannot both
     * succeed.
     *
     * @return the validated record, or null if the requestId/ticket
     *         combination is invalid, expired, or already used.
     */
    public ChatRequestRecord validateAndConsume(String requestId, String ticket) {
        ChatRequestRecord record = records.get(requestId);
        if (record == null) {
            return null;
        }
        if (!record.getTicket().equals(ticket)) {
            return null;
        }
        if (record.isExpired()) {
            records.remove(requestId);
            return null;
        }
        if (!record.markUsedIfNotAlready()) {
            // Already consumed by a prior request -- reject reuse.
            return null;
        }
        return record;
    }

    /**
     * Periodic lazy cleanup of stale entries (expired and/or already used)
     * so the map doesn't leak memory if a ticket is issued but never
     * consumed, or after a stream completes without explicit removal.
     */
    @Scheduled(fixedRate = 30000)
    public void sweepExpired() {
        records.values().removeIf(r -> r.isExpired() || r.isUsed());
    }
}
