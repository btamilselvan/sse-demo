package com.example.apilayer.service;

import com.example.apilayer.model.ChatRequestRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ticket issuance / single-use validation, in isolation from
 * any HTTP layer or the Chat Layer.
 */
class ChatRequestRegistryTest {

    private final ChatRequestRegistry registry = new ChatRequestRegistry(60000);

    @Test
    void registerIssuesRetrievableTicket() {
        ChatRequestRecord record = registry.register("req-1", "alice", "hello");
        assertNotNull(record.getTicket());
        assertEquals("req-1", record.getRequestId());
        assertEquals("alice", record.getUsername());
        assertEquals("hello", record.getMessage());
        assertFalse(record.isUsed());
    }

    @Test
    void validateAndConsumeAcceptsCorrectTicketOnce() {
        ChatRequestRecord record = registry.register("req-2", "bob", "hi there");

        ChatRequestRecord first = registry.validateAndConsume("req-2", record.getTicket());
        assertNotNull(first);
        assertTrue(first.isUsed());

        // Reuse must be rejected.
        ChatRequestRecord second = registry.validateAndConsume("req-2", record.getTicket());
        assertNull(second);
    }

    @Test
    void validateAndConsumeRejectsWrongTicket() {
        registry.register("req-3", "alice", "hello");
        assertNull(registry.validateAndConsume("req-3", "not-the-real-ticket"));
    }

    @Test
    void validateAndConsumeRejectsUnknownRequestId() {
        assertNull(registry.validateAndConsume("does-not-exist", "any-ticket"));
    }

    @Test
    void validateAndConsumeRejectsExpiredTicket() throws Exception {
        ChatRequestRecord record = registry.register("req-4", "charlie", "hello");

        // Force the record's expiry into the past via reflection, since the
        // registry only exposes a fixed TTL constructor arg.
        Field expiresAtField = ChatRequestRecord.class.getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.set(record, Instant.now().minusSeconds(5));

        assertNull(registry.validateAndConsume("req-4", record.getTicket()));
    }

    @Test
    void removeDropsTheRecord() {
        registry.register("req-5", "alice", "hello");
        registry.remove("req-5");
        assertNull(registry.get("req-5"));
    }
}
