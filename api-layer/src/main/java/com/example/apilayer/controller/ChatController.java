package com.example.apilayer.controller;

import com.example.apilayer.dto.ChatDtos.ChatMessageRequest;
import com.example.apilayer.dto.ChatDtos.ChatMessageResponse;
import com.example.apilayer.model.ChatRequestRecord;
import com.example.apilayer.service.ChatRequestRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** SSE connection timeout to the browser -- a safety net in case the
     * Chat Layer or network hangs indefinitely. */
    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ChatRequestRegistry registry;
    private final WebClient chatLayerWebClient;

    public ChatController(ChatRequestRegistry registry, WebClient chatLayerWebClient) {
        this.registry = registry;
        this.chatLayerWebClient = chatLayerWebClient;
    }

    @PostMapping("/messages")
    public org.springframework.http.ResponseEntity<?> submitMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        ChatRequestRecord record = registry.register(request.requestId(), username, request.message());
        return org.springframework.http.ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new ChatMessageResponse(record.getRequestId(), record.getTicket()));
    }

    @GetMapping("/stream/{requestId}")
    public SseEmitter stream(@PathVariable String requestId, @RequestParam String ticket) {
        ChatRequestRecord record = registry.validateAndConsume(requestId, ticket);
        if (record == null) {
            // Ticket invalid/expired/reused, or requestId unknown. Reject
            // before opening any SSE connection.
            throw new TicketRejectedException();
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        Runnable cleanup = () -> {
            Disposable d = subscriptionRef.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
            registry.remove(requestId);
        };

        emitter.onCompletion(cleanup::run);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(throwable -> cleanup.run());

        // UriComponentsBuilder URL-encodes the "message" query param value for
        // us -- do not pre-encode record.getMessage(), that would double-encode it.
        Disposable subscription = chatLayerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/chat/stream/{requestId}")
                        .queryParam("message", "{message}")
                        .build(requestId, record.getMessage()))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .subscribe(
                        sse -> relay(emitter, sse),
                        error -> {
                            log.warn("Chat Layer stream error for requestId={}: {}", requestId, error.toString());
                            sendErrorAndComplete(emitter, "Chat layer unreachable or stream error");
                        },
                        emitter::complete
                );

        subscriptionRef.set(subscription);
        return emitter;
    }

    private void relay(SseEmitter emitter, ServerSentEvent<String> sse) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event();
            if (sse.event() != null) {
                event.name(sse.event());
            }
            if (sse.data() != null) {
                // sse.data() is already a JSON string produced by the Chat
                // Layer -- pass it through as-is (no mediaType given) so it
                // is written verbatim rather than being re-serialized/escaped
                // as a JSON string literal.
                event.data(sse.data());
            }
            emitter.send(event);
        } catch (IOException e) {
            // Browser likely disconnected; emitter's onError/onCompletion will handle cleanup.
            log.debug("Failed to relay SSE event, client likely disconnected: {}", e.toString());
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"" + message.replace("\"", "'") + "\"}"));
        } catch (IOException ignored) {
            // Client already gone; nothing more to do.
        } finally {
            emitter.complete();
        }
    }

    /**
     * Thrown when a ticket is invalid, expired, reused, or the requestId is
     * unknown. Mapped to a 401 JSON error response by
     * {@code GlobalExceptionHandler} -- rejected before any SSE connection
     * is opened.
     */
    public static class TicketRejectedException extends RuntimeException {
    }
}
