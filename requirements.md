Requirements — SSE Chat POC
1. Objective

Build a lightweight chat application to demonstrate how Server-Sent Events (SSE) work across a frontend and two backend services.

The application will have a login page with predefined users and a chat page where users can send messages and receive simulated responses incrementally through SSE.

The primary goal is to understand how the Chat Layer emits SSE events, how the API Layer relays them, and how the browser displays them in real time.

2. Technology Stack

Frontend: Next.js, React, TypeScript (server-side rendering).
API Layer: Java 21, Spring Boot 3, Spring MVC, Spring Security, JWT.
Chat Layer: Java 21, Spring Boot 3, Spring WebFlux.
Build tools: Maven for backend projects, npm for frontend.
Storage: In-memory only. No database.
3. Architecture

The project consists of three independently running applications.


text
Next.js / React (Port 3000)
          |
          | REST + SSE
          v
Spring Boot API Layer (Port 8080)
  - JWT authentication
  - Business logic
  - SSE proxy
          |
          | REST + SSE
          v
Spring Boot Chat Layer (Port 8081)
  - Mock chat responses
  - SSE event producer
The frontend communicates only with the API Layer. The API Layer communicates with the Chat Layer and forwards its SSE events to the browser without buffering the complete response.

4. Functional Requirements

4.1 Authentication

Provide a login page with username and password fields.
Configure three predefined users in memory:
alice / password123
bob / password123
charlie / password123
Validate credentials in the API Layer.
Issue a signed JWT upon successful login.
Use Spring Security to validate JWTs on protected endpoints.
Return HTTP 401 for invalid credentials or missing/invalid tokens.
Provide a logout action that clears the frontend's in-memory token.
Keep authentication stateless. Do not use Spring Session.
These credentials are for local development only.

4.2 Chat UI

Create a protected /chat page that:

Displays a welcome message using the logged-in user's name.
Displays user messages and assistant responses in a chat window.
Provides a text input and Send button.
Displays assistant responses incrementally as SSE events arrive.
Shows basic connection status and errors.
Allows the user to log out.
Use simple styling. No complex UI or state-management libraries are required.

4.3 API Layer

Implement the following endpoints:

POST /api/auth/login — validate credentials and return a JWT.
GET /api/auth/me — return the authenticated user's information.
POST /api/auth/logout — handle frontend logout.
POST /api/chat/messages — accept a message, validate the JWT, register the request, and return a request ID and short-lived SSE ticket.
GET /api/chat/stream/{requestId}?ticket=... — validate the single-use ticket, connect to the Chat Layer, and relay SSE events to the browser.
Use Spring MVC's SseEmitter to relay events and WebClient to consume the Chat Layer's SSE stream.

The API Layer must validate request ownership and reject invalid or reused tickets. The ticket must be short-lived, randomly generated, and scoped to a single chat request. Never put the actual JWT in a URL.

4.4 Chat Layer

Implement a mock SSE endpoint:

GET /chat/stream/{requestId}

The Chat Layer generates a friendly response based on the submitted message and splits it into small chunks.

Emit named SSE events:

message — contains one response chunk.
done — indicates that the response is complete.
error — indicates a streaming failure, when possible.
Example payloads:


text
event: message
data: {"requestId":"abc-123","chunk":"Hello! "}

event: message
data: {"requestId":"abc-123","chunk":"How can I help?"}

event: done
data: {"requestId":"abc-123","status":"completed"}

Emit chunks approximately every 300–500 milliseconds using a non-blocking delay.

Use Spring WebFlux and a reactive Flux-based implementation. The Chat Layer must not communicate directly with the frontend.

4.5 End-to-End Flow

The user logs in and receives a JWT.
The frontend submits a chat message to the API Layer using the JWT.
The API Layer registers the request and returns a short-lived SSE ticket.
The frontend opens a native EventSource connection using that ticket.
The API Layer validates the ticket and subscribes to the Chat Layer's SSE stream.
The Chat Layer emits response chunks incrementally.
The API Layer forwards each event immediately.
The browser appends each chunk to the assistant's message.
On receiving done, the frontend closes the connection and enables the Send button again.
Native EventSource cannot attach a custom Authorization header. Use the short-lived ticket for SSE subscription rather than placing the JWT in the URL.

5. Implementation Constraints

Use three separate applications in a single repository.
Suggested directories: frontend/, api-layer/, and chat-layer/.
Keep requests and tickets in memory.
Do not add a database, Redis, Kafka, Docker, or message brokers.
Do not integrate an actual AI model.
Do not implement JWT refresh or revocation.
Configure CORS for the local frontend origin.
Handle stream errors and clean up resources when connections close.
Keep the implementation readable and minimal.
6. Acceptance Criteria

The POC is complete when:

A predefined user can log in and receive a JWT.
Invalid credentials and unauthenticated API requests are rejected.
An authenticated user can send a chat message.
The Chat Layer emits multiple SSE chunks with visible delays.
The API Layer relays chunks without buffering the entire response.
The browser displays the response progressively in the chat window.
The stream closes when the done event arrives.
Invalid or reused SSE tickets are rejected.
The frontend can send another message after completion.
All three applications run locally using documented commands.

7. Instructions for Claude Code

Read this file and implement the POC incrementally.

Start with the Chat Layer and verify SSE events using curl. Then implement JWT authentication and the SSE proxy in the API Layer. Finally, build the Next.js frontend and verify the complete end-to-end flow.

Add basic tests for authentication, ticket validation, and SSE generation. Include a concise README with setup and run instructions.

Prioritize simplicity and working incremental SSE delivery. Do not add features or infrastructure beyond these requirements.

