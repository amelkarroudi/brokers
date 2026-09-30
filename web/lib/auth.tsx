"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { api, getToken, setToken } from "@/lib/api";
import type { Me, Session } from "@/lib/types";

interface AuthState {
  me: Me | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  logout: () => Promise<void>;
}

export interface SignupInput {
  organizationName: string;
  fullName: string;
  email: string;
  password: string;
  defaultCurrency?: string;
  timezone?: string;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) {
      setLoading(false);
      return;
    }
    api<Me>("/api/auth/me")
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const onUnauthenticated = () => {
      setMe(null);
      router.replace("/login");
    };
    window.addEventListener("brokers:unauthenticated", onUnauthenticated);
    return () => window.removeEventListener("brokers:unauthenticated", onUnauthenticated);
  }, [router]);

  const startSession = useCallback((session: Session) => {
    setToken(session.token);
    setMe(session.me);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      startSession(await api<Session>("/api/auth/login", "POST", { email, password }));
    },
    [startSession],
  );

  const signup = useCallback(
    async (input: SignupInput) => {
      startSession(await api<Session>("/api/auth/signup", "POST", input));
    },
    [startSession],
  );

  const logout = useCallback(async () => {
    try {
      await api("/api/auth/logout", "POST");
    } finally {
      setToken(null);
      setMe(null);
      router.replace("/login");
    }
  }, [router]);

  const value = useMemo(() => ({ me, loading, login, signup, logout }), [me, loading, login, signup, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
