package com.sse.chatlayer.model;

/**
 * Payload for the terminal {@code done} SSE event.
 */
public record DoneEvent(String requestId, String status) {

    public static DoneEvent completed(String requestId) {
        return new DoneEvent(requestId, "completed");
    }
}
