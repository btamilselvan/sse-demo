package com.sse.chatlayer.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Produces a short, friendly, purely-canned response for a given user
 * message, and splits it into small word/phrase chunks suitable for
 * progressive SSE delivery.
 *
 * <p>No real AI or external call is involved - this simulates streaming
 * output for the POC.
 */
@Service
public class ChatResponseService {

    /**
     * Builds the canned reply text for the given user message.
     */
    public String buildResponse(String message) {
        String safeMessage = (message == null || message.isBlank()) ? "(empty message)" : message.trim();
        return "Hello! You said: '" + safeMessage + "'. "
                + "Here's a simulated response to demonstrate SSE streaming. "
                + "Each piece of this message is being sent as a separate chunk, "
                + "with a short delay between them, just like a real streaming assistant would.";
    }

    /**
     * Splits the response text into roughly 4-8 small chunks (word groups),
     * preserving the original spacing so chunks can be concatenated back
     * into the exact original text.
     */
    public List<String> splitIntoChunks(String response) {
        String[] words = response.split(" ");
        int wordCount = words.length;

        // Aim for ~6 chunks, clamped to the 4-8 chunk range requested by the contract.
        int targetChunks = Math.max(4, Math.min(8, (int) Math.ceil(wordCount / 5.0)));
        int wordsPerChunk = Math.max(1, (int) Math.ceil((double) wordCount / targetChunks));

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int wordsInCurrent = 0;

        for (int i = 0; i < wordCount; i++) {
            if (wordsInCurrent > 0) {
                current.append(' ');
            }
            current.append(words[i]);
            wordsInCurrent++;

            boolean lastWord = i == wordCount - 1;
            if (wordsInCurrent >= wordsPerChunk || lastWord) {
                if (!lastWord) {
                    current.append(' ');
                }
                chunks.add(current.toString());
                current = new StringBuilder();
                wordsInCurrent = 0;
            }
        }

        return chunks;
    }
}
