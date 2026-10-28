"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/**
 * Holds the JWT and username purely in memory via React state.
 *
 * This is intentionally NOT persisted to localStorage, sessionStorage, or
 * cookies, per the POC's security requirements: a full page reload (or a
 * fresh tab) loses the session, and the user must log in again. The /chat
 * page redirects back to /login whenever this context has no token.
 */
interface AuthContextValue {
  token: string | null;
  username: string | null;
  login: (token: string, username: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);

  const login = useCallback((newToken: string, newUsername: string) => {
    setToken(newToken);
    setUsername(newUsername);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUsername(null);
  }, []);

  const value = useMemo(
    () => ({ token, username, login, logout }),
    [token, username, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
