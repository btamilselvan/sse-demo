package com.example.apilayer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request/response DTOs for the chat endpoints.
 */
public class ChatDtos {

    public record ChatMessageRequest(
            @NotBlank String message,
            @NotBlank String requestId
    ) {}

    public record ChatMessageResponse(String requestId, String ticket) {}

    private ChatDtos() {}
}
