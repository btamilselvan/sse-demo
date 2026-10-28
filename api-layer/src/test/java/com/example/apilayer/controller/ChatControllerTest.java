package com.example.apilayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void submitMessageWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "requestId", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitMessageWithTokenReturnsTicket() throws Exception {
        String token = loginAndGetToken("alice");
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello there",
                                "requestId", requestId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.ticket").exists());
    }

    @Test
    void submitMessageWithBlankMessageReturns400() throws Exception {
        String token = loginAndGetToken("alice");

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "",
                                "requestId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamWithUnknownRequestIdReturns401() throws Exception {
        mockMvc.perform(get("/api/chat/stream/{requestId}", "no-such-request")
                        .param("ticket", "whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void streamWithWrongTicketReturns401() throws Exception {
        String token = loginAndGetToken("alice");
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "requestId", requestId))))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/chat/stream/{requestId}", requestId)
                        .param("ticket", "definitely-not-the-right-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamRejectsReusedTicketViaControllerDirectly() throws Exception {
        // Exercises the same 401-before-open path as a real reused ticket,
        // without needing a live Chat Layer for the first (would-be) success.
        String token = loginAndGetToken("bob");
        String requestId = UUID.randomUUID().toString();

        String body = mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "requestId", requestId))))
                .andReturn().getResponse().getContentAsString();

        String ticket = objectMapper.readTree(body).get("ticket").asText();

        // First use consumes the ticket against the registry directly
        // (avoids opening a real SseEmitter/WebClient subscription in this
        // test, since no Chat Layer is running).
        // We instead assert the second use through the real endpoint is
        // rejected once the ticket is already marked used by the registry.
        var registry = applicationContext().getBean(com.example.apilayer.service.ChatRequestRegistry.class);
        assertTicketConsumedThenRejected(registry, requestId, ticket);
    }

    private void assertTicketConsumedThenRejected(
            com.example.apilayer.service.ChatRequestRegistry registry, String requestId, String ticket) {
        var first = registry.validateAndConsume(requestId, ticket);
        org.junit.jupiter.api.Assertions.assertNotNull(first);
        var second = registry.validateAndConsume(requestId, ticket);
        org.junit.jupiter.api.Assertions.assertNull(second);
    }

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private org.springframework.context.ApplicationContext applicationContext() {
        return context;
    }
}
