# SSE Chat POC

A lightweight proof-of-concept demonstrating Server-Sent Events (SSE) streaming across three independently-runnable applications:

```
Next.js Frontend (3000)  --REST + SSE-->  API Layer (8080)  --REST + SSE-->  Chat Layer (8081)
```

- **Frontend** (`frontend/`): Next.js + React + TypeScript (SSR). Login page + protected chat page. Talks only to the API Layer.
- **API Layer** (`api-layer/`): Java 21, Spring Boot, Spring MVC, Spring Security, JWT. Authenticates users, issues JWTs, registers chat requests, issues single-use SSE tickets, and relays SSE events from the Chat Layer to the browser via `SseEmitter`.
- **Chat Layer** (`chat-layer/`): Java 21, Spring Boot, Spring WebFlux. Generates a simulated response, splits it into chunks, and emits them as SSE events with a short delay between each. Never talks to the browser directly.

All state is in-memory. No database, Docker, Redis, Kafka, or external AI service is used.

## Predefined users (local dev only)

| username | password    |
|----------|-------------|
| alice    | password123 |
| bob      | password123 |
| charlie  | password123 |

## Prerequisites

- Java 21
- Maven (or use each project's `mvnw` if present)
- Node.js 18+ and npm

## Configuration

The API Layer reads these properties (all have sensible local-dev defaults, override via env var):

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `app.jwt.secret` | `JWT_SECRET` | dev-only built-in value | HS256 signing key for JWTs — **do not use the default outside local dev** |
| `app.jwt.expiration-ms` | — | `3600000` (1h) | JWT expiry |
| `app.chat-layer.base-url` | `CHAT_LAYER_BASE_URL` | `http://localhost:8081` | Where the API Layer reaches the Chat Layer |
| `app.ticket.ttl-ms` | — | `60000` | SSE ticket time-to-live |
| `app.cors.allowed-origin` | `FRONTEND_ORIGIN` | `http://localhost:3000` | Allowed CORS origin for the frontend |

No secrets are committed to source control; set `JWT_SECRET` yourself for anything beyond local experimentation.

## Running locally

Start all three applications in this order (each in its own terminal):

```bash
# 1. Chat Layer — port 8081
cd chat-layer
mvn spring-boot:run

# 2. API Layer — port 8080
cd api-layer
mvn spring-boot:run

# 3. Frontend — port 3000
cd frontend
npm install
npm run dev
```

Then open **http://localhost:3000** and log in as `alice` / `password123`.

## Running tests

```bash
cd chat-layer && mvn test   # 6 tests: canned response generation, chunking, SSE event sequencing
cd api-layer   && mvn test  # 19 tests: login, /me, ticket issuance, ticket reuse/expiry/mismatch rejection
cd frontend    && npx tsc --noEmit && npm run lint && npm run build
```

## End-to-end manual verification

This is the flow used to confirm the full system works together (all three apps running):

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")

# 2. Confirm identity
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# 3. Submit a chat message, get a requestId + single-use ticket
RESP=$(curl -s -X POST http://localhost:8080/api/chat/messages \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"Hello there!","requestId":"'"$(python3 -c 'import uuid;print(uuid.uuid4())')"'"}')
echo "$RESP"

# 4. Open the SSE stream with the ticket (expect several `message` events, then `done`)
REQ_ID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['requestId'])")
TICKET=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['ticket'])")
curl -N "http://localhost:8080/api/chat/stream/$REQ_ID?ticket=$TICKET"

# 5. Reuse the same ticket — expect 401 (single-use enforcement)
curl -i "http://localhost:8080/api/chat/stream/$REQ_ID?ticket=$TICKET"
```

Expected SSE output for step 4:

```
event:message
data:{"requestId":"...","chunk":"Hello! You said: 'Hello there!'. "}

event:message
data:{"requestId":"...","chunk":"Here's a simulated response to "}

...

event:done
data:{"requestId":"...","status":"completed"}
```

In the browser: log in, send a message, and watch the assistant's reply render progressively chunk-by-chunk as the SSE events arrive, with the Send button disabled until `done` is received. Invalid credentials show an error on the login page; a reused or invalid ticket is rejected by the API Layer before any stream is opened.

## Known POC limitations

- The JWT is held only in memory on the frontend (per spec) — refreshing the browser logs the user out.
- No JWT refresh/revocation; tokens simply expire after 1 hour.
- The "AI" response is a canned template that echoes the user's message — there is no real language model involved.
