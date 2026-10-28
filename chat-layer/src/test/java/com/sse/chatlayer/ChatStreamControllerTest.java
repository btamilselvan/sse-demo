package com.sse.chatlayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import com.sse.chatlayer.controller.ChatStreamController;
import com.sse.chatlayer.service.ChatResponseService;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatStreamControllerTest {

    private final ChatStreamController controller = new ChatStreamController(new ChatResponseService());

    @Test
    void streamEmitsMessageChunksFollowedByDone() {
        Flux<ServerSentEvent<Object>> stream = controller.stream("req-1", "hello there");

        List<ServerSentEvent<Object>> events = stream
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(events).isNotNull();
        assertThat(events.size()).isGreaterThanOrEqualTo(2);

        // All but the last event should be "message" events.
        List<ServerSentEvent<Object>> allButLast = events.subList(0, events.size() - 1);
        assertThat(allButLast).allMatch(e -> "message".equals(e.event()));

        // The last event must be "done".
        ServerSentEvent<Object> last = events.get(events.size() - 1);
        assertThat(last.event()).isEqualTo("done");
    }

    @Test
    void streamRespectsApproximateChunkSpacing() {
        StepVerifier.withVirtualTime(() -> controller.stream("req-2", "hello there, how are you?"))
                .thenAwait(Duration.ofSeconds(10))
                .recordWith(ArrayList::new)
                .thenConsumeWhile(e -> true)
                .consumeRecordedWith(events -> {
                    List<ServerSentEvent<Object>> list = new ArrayList<>(events);
                    // 4-8 message chunks + 1 terminal done event.
                    assertThat(list.size()).isBetween(5, 9);
                    assertThat(list.get(list.size() - 1).event()).isEqualTo("done");
                })
                .verifyComplete();
    }
}
