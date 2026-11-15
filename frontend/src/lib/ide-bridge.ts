/*
 * Gọi plugin Mini LeetCode qua built-in server của IntelliJ (specs/practice-ide-rich-editor.spec.md §4).
 * Mọi request đều "simple" (GET, hoặc POST text/plain) để trình duyệt không gửi preflight CORS.
 */

const PORTS = Array.from({ length: 10 }, (_, i) => 63342 + i); // nhiều IDE cùng chạy dùng cổng kế tiếp
const PORT_KEY = "ide-bridge-port";
const base = (port: number) => `http://127.0.0.1:${port}/api/mini-leetcode`;

export interface IdeStatus { port: number; version: string; connected: boolean }

async function call<T>(port: number, action: string, body?: unknown, timeoutMs = 1500): Promise<T> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(`${base(port)}?action=${action}`, {
      method: body === undefined ? "GET" : "POST",
      headers: body === undefined ? undefined : { "Content-Type": "text/plain" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal,
      cache: "no-store",
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((data as { error?: string }).error ?? `IntelliJ answered ${res.status}`);
    return data as T;
  } finally {
    clearTimeout(t);
  }
}

const rememberedPort = () => {
  try { return Number(sessionStorage.getItem(PORT_KEY)) || null; } catch { return null; }
};
const rememberPort = (port: number) => {
  try { sessionStorage.setItem(PORT_KEY, String(port)); } catch { /* bỏ qua */ }
};

/** Tìm IntelliJ đang chạy có plugin; null nếu không thấy. */
export async function findIde(): Promise<IdeStatus | null> {
  const first = rememberedPort();
  for (const port of first ? [first, ...PORTS.filter((p) => p !== first)] : PORTS) {
    try {
      const s = await call<{ version: string; connected: boolean }>(port, "ping", undefined, port === first ? 1500 : 600);
      rememberPort(port);
      return { port, version: s.version, connected: !!s.connected };
    } catch {
      /* cổng không có IDE hoặc không có plugin: thử cổng sau */
    }
  }
  return null;
}

export const connectIde = (port: number, serverUrl: string, token: string) =>
  call<{ connected: boolean }>(port, "connect", { serverUrl, token }, 15_000);

export const openInIde = (port: number, target: { planSlug: string; itemId: number; stageNo: number }) =>
  call<{ accepted: boolean }>(port, "open", target, 5_000);
