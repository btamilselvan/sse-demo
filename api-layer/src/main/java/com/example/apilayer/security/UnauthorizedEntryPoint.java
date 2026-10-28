package com.example.apilayer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.apilayer.dto.AuthDtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures every authentication failure (missing/invalid/expired token) on a
 * protected endpoint returns 401 with a JSON body, never Spring Security's
 * default 403 or a redirect.
 */
@Component
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(new ErrorResponse("Unauthorized: missing or invalid token")));
    }
}
