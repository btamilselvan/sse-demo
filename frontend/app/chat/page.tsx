"use client";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import {
  buildStreamUrl,
  fetchMe,
  logout as apiLogout,
  sendChatMessage,
} from "@/lib/api";
import styles from "./chat.module.css";

interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "error";
  text: string;
}

type StreamStatus = "idle" | "connecting" | "streaming" | "error";

const STATUS_LABEL: Record<StreamStatus, string> = {
  idle: "Ready",
  connecting: "Connecting…",
  streaming: "Receiving response…",
  error: "Error",
};

export default function ChatPage() {
  const router = useRouter();
  const { token, username, logout: clearAuth } = useAuth();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState<StreamStatus>("idle");
  const [statusDetail, setStatusDetail] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [displayName, setDisplayName] = useState<string | null>(username);

  const eventSourceRef = useRef<EventSource | null>(null);
  const assistantMessageIdRef = useRef<string | null>(null);
  const chatWindowRef = useRef<HTMLDivElement | null>(null);

  // No in-memory token (e.g. the user refreshed the page, which clears
  // React state) -- bounce back to the login page.
  useEffect(() => {
    if (!token) {
      router.replace("/login");
    }
  }, [token, router]);

  // Confirm the session against the API Layer and refresh the display name.
  useEffect(() => {
    if (!token) return;
    fetchMe(token)
      .then((me) => {
        if (typeof me.username === "string") {
          setDisplayName(me.username);
        }
      })
      .catch(() => {
        clearAuth();
        router.replace("/login");
      });
  }, [token, clearAuth, router]);

  // Close any open stream when the page unmounts.
  useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  // Keep the chat window scrolled to the latest message.
  useEffect(() => {
    const el = chatWindowRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  const closeStream = useCallback(() => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    assistantMessageIdRef.current = null;
  }, []);

  const openStream = useCallback(
    (requestId: string, ticket: string) => {
      // Never put the JWT in this URL -- only the short-lived, single-use
      // ticket returned by POST /api/chat/messages.
      const url = buildStreamUrl(requestId, ticket);
      const es = new EventSource(url);
      eventSourceRef.current = es;

      setStatus("connecting");
      setStatusDetail(null);

      es.addEventListener("open", () => {
        setStatus("streaming");
      });

      es.addEventListener("message", (event: MessageEvent) => {
        try {
          const payload = JSON.parse(event.data) as {
            requestId: string;
            chunk: string;
          };
          setStatus("streaming");
          if (!assistantMessageIdRef.current) {
            const newId = `${requestId}-assistant`;
            assistantMessageIdRef.current = newId;
            setMessages((prev) => [
              ...prev,
              { id: newId, role: "assistant", text: payload.chunk },
            ]);
          } else {
            const id = assistantMessageIdRef.current;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === id ? { ...m, text: m.text + payload.chunk } : m
              )
            );
          }
        } catch {
          // Ignore a malformed chunk; the stream continues.
        }
      });

      es.addEventListener("done", () => {
        setStatus("idle");
        setStatusDetail(null);
        setSending(false);
        closeStream();
      });

      // The server-defined "error" named event AND the browser's native
      // connection-level error both surface through this same listener,
      // since EventSource only has one "error" event type. We distinguish
      // them by whether a JSON payload is present.
      es.addEventListener("error", (event: Event) => {
        const messageEvent = event as MessageEvent;
        let message = "Connection to the stream was lost.";

        if (typeof messageEvent.data === "string" && messageEvent.data.length > 0) {
          try {
            const payload = JSON.parse(messageEvent.data) as {
              message?: string;
            };
            message = payload.message ?? message;
          } catch {
            // Fall back to the generic message below.
          }
          setMessages((prev) => [
            ...prev,
            {
              id: `${requestId}-error-${Date.now()}`,
              role: "error",
              text: message,
            },
          ]);
        }

        setStatus("error");
        setStatusDetail(message);
        setSending(false);
        closeStream();
      });
    },
    [closeStream]
  );

  async function handleSend(event: FormEvent) {
    event.preventDefault();
    const text = input.trim();
    if (!text || sending || !token) return;

    setInput("");
    setSending(true);
    setStatus("connecting");
    setStatusDetail(null);

    const requestId = crypto.randomUUID();
    setMessages((prev) => [
      ...prev,
      { id: `${requestId}-user`, role: "user", text },
    ]);

    try {
      const { ticket } = await sendChatMessage(token, text, requestId);
      openStream(requestId, ticket);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to send message.";
      setStatus("error");
      setStatusDetail(message);
      setSending(false);
      setMessages((prev) => [
        ...prev,
        { id: `${requestId}-error-${Date.now()}`, role: "error", text: message },
      ]);
    }
  }

  function handleLogout() {
    if (token) {
      apiLogout(token).catch(() => {
        // Best-effort; we log out locally regardless.
      });
    }
    closeStream();
    clearAuth();
    router.replace("/login");
  }

  if (!token) {
    return null;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>SSE Chat POC</h1>
          <p className={styles.welcome}>
            Welcome, {displayName ?? username ?? "…"}
          </p>
        </div>
        <button type="button" className={styles.logoutButton} onClick={handleLogout}>
          Logout
        </button>
      </header>

      <div className={styles.statusBar} data-status={status}>
        <span className={styles.statusDot} data-status={status} />
        {STATUS_LABEL[status]}
        {statusDetail ? `: ${statusDetail}` : ""}
      </div>

      <div className={styles.chatWindow} ref={chatWindowRef}>
        {messages.length === 0 && (
          <p className={styles.emptyHint}>Say hello to start the conversation.</p>
        )}
        {messages.map((m) => (
          <div
            key={m.id}
            className={
              m.role === "user"
                ? styles.userRow
                : m.role === "error"
                ? styles.errorRow
                : styles.assistantRow
            }
          >
            <span
              className={
                m.role === "user"
                  ? styles.userBubble
                  : m.role === "error"
                  ? styles.errorBubble
                  : styles.assistantBubble
              }
            >
              {m.text}
            </span>
          </div>
        ))}
      </div>

      <form className={styles.composer} onSubmit={handleSend}>
        <input
          type="text"
          className={styles.input}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a message…"
          disabled={sending}
        />
        <button
          type="submit"
          className={styles.sendButton}
          disabled={sending || input.trim().length === 0}
        >
          Send
        </button>
      </form>
    </main>
  );
}
