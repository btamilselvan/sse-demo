package com.sse.chatlayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chat Layer - SSE Chat POC.
 *
 * <p>Standalone Spring WebFlux service that emits a simulated, chunked chat
 * response over Server-Sent Events. This service is only ever called
 * internally by the API Layer and has no authentication of its own.
 */
@SpringBootApplication
public class ChatLayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatLayerApplication.class, args);
    }
}
