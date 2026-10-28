package com.sse.chatlayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sse.chatlayer.service.ChatResponseService;

class ChatResponseServiceTest {

    private final ChatResponseService service = new ChatResponseService();

    @Test
    void buildResponseIncludesOriginalMessage() {
        String response = service.buildResponse("hello there");

        assertThat(response).contains("hello there");
        assertThat(response).startsWith("Hello! You said:");
    }

    @Test
    void buildResponseHandlesBlankMessage() {
        String response = service.buildResponse("   ");

        assertThat(response).contains("(empty message)");
    }

    @Test
    void splitIntoChunksProducesBetweenFourAndEightChunks() {
        String response = service.buildResponse("hello there, how are you doing today?");

        List<String> chunks = service.splitIntoChunks(response);

        assertThat(chunks.size()).isBetween(4, 8);
    }

    @Test
    void splitIntoChunksReconstructsOriginalText() {
        String response = service.buildResponse("hello there");

        List<String> chunks = service.splitIntoChunks(response);
        String rebuilt = String.join("", chunks);

        assertThat(rebuilt).isEqualTo(response);
    }
}
