import type { Role } from "@/types/api";

const ACCESS = "access_token";
const REFRESH = "refresh_token";

export interface AuthUser { id: number; username: string; role: Role }

const safe = <T,>(fn: () => T, fallback: T): T => { try { return fn(); } catch { return fallback; } };

export const getToken = () => (typeof window === "undefined" ? null : safe(() => localStorage.getItem(ACCESS), null));
export const getRefreshToken = () => (typeof window === "undefined" ? null : safe(() => localStorage.getItem(REFRESH), null));

export function setTokens(access: string, refresh?: string) {
  safe(() => {
    localStorage.setItem(ACCESS, access);
    if (refresh) localStorage.setItem(REFRESH, refresh);
  }, undefined);
}
export function clearTokens() {
  safe(() => { localStorage.removeItem(ACCESS); localStorage.removeItem(REFRESH); }, undefined);
}

// Chỉ để HIỂN THỊ. Quyền thật sự do server kiểm tra (403).
export function decodeUser(token: string | null): AuthUser | null {
  if (!token) return null;
  try {
    const b64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const p = JSON.parse(atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), "=")));
    return { id: Number(p.userId ?? p.sub ?? 0), username: String(p.username ?? p.sub ?? "user"), role: (p.role ?? "USER") as Role };
  } catch {
    return null;
  }
}
