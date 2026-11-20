import type { Role } from "@/types/api";

const ACCESS = "access_token";
const REFRESH = "refresh_token";

export interface AuthUser { id: number; username: string; role: Role }

const safe = <T,>(fn: () => T, fallback: T): T => { try { return fn(); } catch { return fallback; } };

// ADMIN bấm "View site" thì được xem trang học viên tới hết session tab; không có cờ thì bị đưa về /admin.
const VIEW_SITE = "admin_view_site";
export const isViewingSite = () => safe(() => sessionStorage.getItem(VIEW_SITE) === "1", false);
export const setViewingSite = (on: boolean) =>
  safe(() => (on ? sessionStorage.setItem(VIEW_SITE, "1") : sessionStorage.removeItem(VIEW_SITE)), undefined);

export const getToken = () => (typeof window === "undefined" ? null : safe(() => localStorage.getItem(ACCESS), null));
export const getRefreshToken = () => (typeof window === "undefined" ? null : safe(() => localStorage.getItem(REFRESH), null));

/** Phát khi token đổi trong tab hiện tại, để useUser cập nhật header ngay. */
export const AUTH_CHANGED = "auth:changed";
const emitAuthChanged = () => { if (typeof window !== "undefined") window.dispatchEvent(new Event(AUTH_CHANGED)); };

export function setTokens(access: string, refresh?: string) {
  safe(() => {
    localStorage.setItem(ACCESS, access);
    if (refresh) localStorage.setItem(REFRESH, refresh);
  }, undefined);
  emitAuthChanged();
}
export function clearTokens() {
  safe(() => { localStorage.removeItem(ACCESS); localStorage.removeItem(REFRESH); sessionStorage.removeItem(VIEW_SITE); }, undefined);
  emitAuthChanged();
}

/** Thời điểm hết hạn (ms, theo `exp` trong JWT). `null` khi token không có `exp` hoặc không đọc được. */
export function tokenExpiresAt(token: string | null): number | null {
  if (!token) return null;
  try {
    const b64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const p = JSON.parse(atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), "=")));
    return typeof p.exp === "number" ? p.exp * 1000 : null;
  } catch {
    return null;
  }
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
