package com.sse.chatlayer.model;

/**
 * Payload for a {@code message} SSE event: one chunk of the simulated
 * assistant response.
 */
public record MessageEvent(String requestId, String chunk) {
}
