package com.sse.chatlayer.controller;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sse.chatlayer.model.DoneEvent;
import com.sse.chatlayer.model.ErrorEvent;
import com.sse.chatlayer.model.MessageEvent;
import com.sse.chatlayer.service.ChatResponseService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Exposes the mock SSE chat endpoint consumed by the API Layer.
 *
 * <p>This service is internal-only: it is never called directly by the
 * browser, so no authentication or CORS configuration is applied here.
 */
@RestController
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    /** Spacing between chunk emissions, within the 300-500ms range requested by the contract. */
    private static final Duration CHUNK_DELAY = Duration.ofMillis(400);

    private final ChatResponseService chatResponseService;

    public ChatStreamController(ChatResponseService chatResponseService) {
        this.chatResponseService = chatResponseService;
    }

    /**
     * Streams a simulated chat response as SSE {@code message} events
     * followed by a terminal {@code done} event (or an {@code error} event
     * if something goes wrong mid-stream).
     */
    @GetMapping(path = "/chat/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(
            @PathVariable String requestId,
            @RequestParam(name = "message", defaultValue = "") String message) {

        log.info("Starting chat stream for requestId={}", requestId);

        String response = chatResponseService.buildResponse(message);
        List<String> chunks = chatResponseService.splitIntoChunks(response);

        Flux<ServerSentEvent<Object>> messageEvents = Flux.fromIterable(chunks)
                .delayElements(CHUNK_DELAY)
                .map(chunk -> toEvent("message", new MessageEvent(requestId, chunk)));

        Mono<ServerSentEvent<Object>> doneEvent = Mono.just(toEvent("done", DoneEvent.completed(requestId)));

        return messageEvents
                .concatWith(doneEvent)
                .onErrorResume(ex -> {
                    log.warn("Chat stream failed for requestId={}: {}", requestId, ex.getMessage());
                    return Flux.just(toEvent("error",
                            new ErrorEvent(requestId, "Streaming failed: " + ex.getMessage())));
                })
                .doOnCancel(() -> log.info("Client disconnected early for requestId={}", requestId))
                .doFinally(signal -> log.info("Chat stream finished for requestId={} signal={}", requestId, signal));
    }

    private static ServerSentEvent<Object> toEvent(String eventName, Object payload) {
        return ServerSentEvent.builder(payload)
                .event(eventName)
                .build();
    }
}
