"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { ApiError, login } from "@/lib/api";
import styles from "./login.module.css";

export default function LoginPage() {
  const router = useRouter();
  const { login: setAuth } = useAuth();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const result = await login(username, password);
      setAuth(result.token, result.username);
      router.push("/chat");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("Invalid username or password.");
      } else {
        setError(
          err instanceof Error
            ? `Login failed: ${err.message}`
            : "Login failed. Please try again."
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.container}>
      <form className={styles.card} onSubmit={handleSubmit}>
        <h1 className={styles.title}>SSE Chat POC</h1>
        <p className={styles.hint}>
          Demo users: <strong>alice</strong>, <strong>bob</strong>, or{" "}
          <strong>charlie</strong> — password: <strong>password123</strong>
        </p>

        <label className={styles.label} htmlFor="username">
          Username
        </label>
        <input
          id="username"
          name="username"
          className={styles.input}
          placeholder="alice"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label className={styles.label} htmlFor="password">
          Password
        </label>
        <input
          id="password"
          name="password"
          type="password"
          className={styles.input}
          placeholder="password123"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        {error && <p className={styles.error}>{error}</p>}

        <button className={styles.button} type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
