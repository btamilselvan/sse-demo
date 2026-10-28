/**
 * Thin client for the API Layer (http://localhost:8080). The frontend talks
 * only to this service -- never directly to the Chat Layer (port 8081).
 */

export const API_BASE_URL = "http://localhost:8080";

export interface LoginResponse {
  token: string;
  username: string;
}

export interface MeResponse {
  username: string;
  [key: string]: unknown;
}

export interface ChatMessageResponse {
  requestId: string;
  ticket: string;
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function parseJsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Request failed with status ${res.status}`;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string") {
        message = body.message;
      }
    } catch {
      // Response had no JSON body; fall back to the generic message above.
    }
    throw new ApiError(res.status, message);
  }
  return (await res.json()) as T;
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  return parseJsonOrThrow<LoginResponse>(res);
}

export async function fetchMe(token: string): Promise<MeResponse> {
  const res = await fetch(`${API_BASE_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return parseJsonOrThrow<MeResponse>(res);
}

export async function logout(token: string): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    // Best-effort only; the frontend clears its in-memory token regardless.
  }
}

export async function sendChatMessage(
  token: string,
  message: string,
  requestId: string
): Promise<ChatMessageResponse> {
  const res = await fetch(`${API_BASE_URL}/api/chat/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ message, requestId }),
  });
  return parseJsonOrThrow<ChatMessageResponse>(res);
}

/**
 * Builds the EventSource URL for the SSE stream. Only the short-lived,
 * single-use ticket is included -- the real JWT must never appear in a URL.
 */
export function buildStreamUrl(requestId: string, ticket: string): string {
  const params = new URLSearchParams({ ticket });
  return `${API_BASE_URL}/api/chat/stream/${encodeURIComponent(requestId)}?${params.toString()}`;
}
