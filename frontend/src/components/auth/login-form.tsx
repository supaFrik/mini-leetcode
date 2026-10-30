"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthPage } from "@/components/auth/auth-page";
import { ApiError, USE_MOCK, errorMessage } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { login, register } from "@/lib/services";

// Prevent open-redirect (e.g., //evil.com)
const safeNext = (n: string | null) => (n && n.startsWith("/") && !n.startsWith("//") && !n.startsWith("/login") && !n.startsWith("/register") ? n : "/");

/**
 * Auth flow controller.
 *
 * Owns all state, validation and network calls. The visual shell lives in
 * `components/auth/auth-page.tsx` (the "Aurora" split-screen surface).
 */
export function LoginForm({ initialMode = "signin" }: { initialMode?: "signin" | "signup" } = {}) {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const mode = initialMode;
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (getToken()) router.replace(next); }, [router, next]);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true); setError(null); setFields({});
    try { await fn(); router.replace(next); }
    catch (err) {
      setError(errorMessage(err, "Login failed."));
      if (err instanceof ApiError && err.problem?.invalid_params) setFields(err.problem.invalid_params);
    } finally { setBusy(false); }
  };

  const submit = () => {
    if (username.trim().length < 3) { setFields({ username: "Username must be at least 3 characters." }); return; }
    if (mode === "signup") {
      const emailVal = email.trim();
      if (!emailVal) { setFields({ email: "Email cannot be empty." }); return; }
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(emailVal)) { setFields({ email: "Invalid email format." }); return; }
    }
    if (!USE_MOCK && password.length < 8) { setFields({ password: "Password must be at least 8 characters." }); return; }
    void run(() => (mode === "signin" ? login(username.trim(), password) : register(username.trim(), email.trim(), password)));
  };

  const handleSubmit = () => submit();

  return (
    <AuthPage
      mode={mode}
      username={username}
      email={email}
      password={password}
      showPassword={show}
      busy={busy}
      error={error}
      fieldErrors={fields}
      onUsernameChange={setUsername}
      onEmailChange={setEmail}
      onPasswordChange={setPassword}
      onToggleShowPassword={() => setShow(!show)}
      onSubmit={handleSubmit}
    />
  );
}
