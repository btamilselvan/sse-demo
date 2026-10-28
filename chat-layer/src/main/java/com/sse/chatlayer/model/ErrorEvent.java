package com.sse.chatlayer.model;

/**
 * Payload for an {@code error} SSE event, emitted when the stream fails
 * mid-flight.
 */
public record ErrorEvent(String requestId, String message) {
}
