# CLAUDE.md — SSE Chat POC

## Project Objective

Build a lightweight, local SSE Chat POC consisting of three independent applications:

- **Frontend**: Next.js, React, and TypeScript with SSR.
- **API Layer**: Java 21, Spring Boot, Spring MVC, Spring Security, and JWT.
- **Chat Layer**: Java 21, Spring Boot, and Spring WebFlux.

Read `requirements.md` before implementing any functionality. It is the source of truth for functional requirements and acceptance criteria.

## Architecture

The request and event flow is:

```
Browser → API Layer → Chat Layer
Chat Layer SSE events → API Layer SSE proxy → Browser
```

The frontend must communicate only with the API Layer. The API Layer authenticates users, validates JWTs, manages chat requests, and relays SSE events. The Chat Layer generates simulated responses and emits SSE events.

Default ports:

| Application | Port |
|---|---|
| Frontend | 3000 |
| API Layer | 8080 |
| Chat Layer | 8081 |

## Parallel Implementation Rules

The project may be implemented using Claude Code subagents or agent teams.

Divide implementation into three independent workstreams:

- Frontend agent: owns `frontend/`.
- API agent: owns `api-layer/`.
- Chat agent: owns `chat-layer/`.
- An integration/testing agent may inspect all applications, run tests, and recommend fixes.

Each implementation agent must:

- Read `requirements.md` and this file before starting.
- Work primarily within its assigned directory.
- Avoid modifying files owned by another implementation agent.
- Communicate API contracts, assumptions, and configuration requirements to the coordinating agent.
- Implement and test its application independently.
- Report completed work, test results, and any blockers.

The coordinating agent owns cross-application integration, shared documentation, and final acceptance testing.

Do not have multiple agents independently implement the same feature or modify the same files concurrently. If a shared change is needed, coordinate ownership before making it.

## Integration Contracts

Agree on the following contracts before implementing the applications in parallel.

### Authentication

- `POST /api/auth/login` issues a signed JWT.
- `GET /api/auth/me` returns the authenticated user's information.
- Protected REST endpoints require `Authorization: Bearer <token>`.
- Authentication is stateless. Do not use Spring Session.
- For this POC, the frontend stores the JWT in memory.

### Chat Requests and SSE

- `POST /api/chat/messages` accepts a message and request ID and returns an accepted response with a short-lived, single-use SSE ticket.
- `GET /api/chat/stream/{requestId}?ticket=...` validates the ticket and streams events to the browser.
- The API Layer obtains the message and metadata from its request registry and passes them to the Chat Layer.
- The Chat Layer emits named `message`, `done`, and, where possible, `error` events with JSON payloads.
- The API Layer relays events immediately without buffering the complete response.
- The frontend uses native `EventSource`. Never put the actual JWT in a URL.

Document any agreed changes to these contracts before implementing them.

## Coding Guidelines

- Keep the implementation lightweight and easy to understand.
- Use Java 21 and compatible Spring Boot 3.x dependencies.
- Use Maven for backend applications and npm for the frontend.
- Keep each application independently runnable.
- Use in-memory storage only.
- Do not introduce a database, Redis, Kafka, Docker, WebSockets, or an external AI service.
- Do not add unnecessary libraries, abstractions, or infrastructure.
- Keep secrets out of source control.
- Configure CORS for the local frontend origin.
- Handle errors and clean up SSE connections when streams finish or clients disconnect.

## Testing and Completion

Each implementation agent must run the relevant available tests and report their results.

The coordinating agent must verify the end-to-end flow:

- Login and obtain a JWT.
- Submit an authenticated chat message.
- Open the SSE subscription using the ticket.
- Receive multiple chunks from the Chat Layer through the API Layer.
- Display chunks progressively in the browser.
- Receive `done` and close the stream.
- Verify authentication failures, ticket validation, and stream cleanup.

Do not claim a test passed unless it was actually executed.

Update `README.md` with application setup, configuration, startup commands, and end-to-end testing instructions.

Prioritize a working POC over additional features.
