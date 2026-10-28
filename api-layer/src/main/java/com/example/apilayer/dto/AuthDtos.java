package com.example.apilayer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request/response DTOs for the auth endpoints.
 */
public class AuthDtos {

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record LoginResponse(String token, String username) {}

    public record MeResponse(String username) {}

    public record ErrorResponse(String error) {}

    private AuthDtos() {}
}
