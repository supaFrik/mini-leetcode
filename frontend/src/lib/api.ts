import { clearTokens, getRefreshToken, getToken, setTokens, tokenExpiresAt } from "./auth";
import type { ApiResponse, ProblemDetail } from "@/types/api";

export const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK === "true";

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
  }
}

// Chênh lệch giữa giờ server và giờ máy, cập nhật từ trường `timestamp` của mỗi response.
let serverOffsetMs = 0;
export const getServerOffset = () => serverOffsetMs;

const emit = (name: string) => { if (typeof window !== "undefined") window.dispatchEvent(new Event(name)); };

/**
 * Kết quả một lần refresh:
 * - `ok`: đã có access token mới.
 * - `rejected`: server từ chối refresh token (4xx) hoặc không có refresh token → đã xoá token.
 * - `unavailable`: lỗi mạng hoặc server 5xx → GIỮ token, người dùng không bị đăng xuất.
 */
type RefreshResult = { kind: "ok" } | { kind: "rejected" } | { kind: "unavailable"; error: Error };

// Refresh token xoay vòng (RFC 6819): chỉ cho phép MỘT lần refresh tại một thời điểm,
// nếu không 2 request song song sẽ làm server tưởng token bị đánh cắp và huỷ cả họ token.
// Cả refresh sớm (token sắp/đã hết hạn) lẫn refresh khi gặp 401 đều đi qua hàm này.
let refreshing: Promise<RefreshResult> | null = null;
function tryRefresh(): Promise<RefreshResult> {
  if (refreshing) return refreshing;
  const rt = getRefreshToken();
  if (!rt) return Promise.resolve({ kind: "rejected" });
  refreshing = (async (): Promise<RefreshResult> => {
    try {
      let res: Response;
      try {
        res = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: rt }),
        });
      } catch (e) {
        emit("net:error");
        return { kind: "unavailable", error: e instanceof Error ? e : new TypeError("Failed to fetch") };
      }
      const body = await res.json().catch(() => null);
      if (res.status >= 500) return { kind: "unavailable", error: new ApiError(res.status, body as ProblemDetail | null) };
      const d = body?.data ?? body;
      if (!res.ok || !d?.accessToken) {
        clearTokens();
        return { kind: "rejected" };
      }
      setTokens(d.accessToken, d.refreshToken);
      return { kind: "ok" };
    } finally {
      setTimeout(() => { refreshing = null; }, 0);
    }
  })();
  return refreshing;
}

/** Access token đã hết hạn (hoặc còn dưới 5 giây), tính theo giờ server. */
const EXPIRY_SKEW_MS = 5000;
function isExpired(token: string | null) {
  const exp = tokenExpiresAt(token);
  return exp != null && exp - EXPIRY_SKEW_MS <= Date.now() + serverOffsetMs;
}

export async function request<T>(path: string, init: RequestInit = {}, canRetry = true) {
  // Login, register, refresh... không bao giờ kích hoạt refresh.
  const isAuthCall = path.startsWith("/api/v1/auth/");

  // Refresh sớm: endpoint công khai không trả 401 với token hết hạn (server coi là khách),
  // nên phải tự làm mới trước khi gửi, kẻo trang hiển thị như đã đăng nhập mà server không nhận ra.
  if (!isAuthCall && canRetry && getRefreshToken() && isExpired(getToken())) {
    const r = await tryRefresh();
    if (r.kind === "unavailable") throw r.error; // giữ token, báo lỗi cho trang
    // `rejected`: token đã bị xoá, request tiếp tục với tư cách khách.
  }

  const token = getToken();
  const isForm = typeof FormData !== "undefined" && init.body instanceof FormData;
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: {
        ...(init.body && !isForm ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    });
  } catch (e) {
    if ((e as Error).name !== "AbortError") emit("net:error"); // bật banner "mất kết nối"
    throw e;
  }
  emit("net:ok");

  const body = await res.json().catch(() => null);
  const ts = body?.timestamp ? Date.parse(body.timestamp) : NaN;
  if (!Number.isNaN(ts)) serverOffsetMs = ts - Date.now();

  if (res.status === 401 && canRetry && !isAuthCall) {
    const r = await tryRefresh();
    // Chỉ thử lại đúng một lần (canRetry = false), nên không thể lặp refresh vô hạn.
    if (r.kind === "ok") return request<T>(path, init, false);
    if (r.kind === "unavailable") throw r.error; // giữ token, không đăng xuất
  }
  if (!res.ok) {
    if (res.status === 401 && !isAuthCall && typeof window !== "undefined") {
      clearTokens();
      window.location.href = "/login";
    }
    throw new ApiError(res.status, body as ProblemDetail | null);
  }
  const resultData = (body && typeof body === "object" && "data" in body && (body as ApiResponse<T>).data !== undefined)
    ? (body as ApiResponse<T>).data
    : (body as T);
  return { data: resultData, status: res.status, headers: res.headers };
}

export async function api<T>(path: string, init?: RequestInit) {
  return (await request<T>(path, init)).data;
}

export const errorMessage = (e: unknown, fallback = "An unexpected error occurred. Please try again.") =>
  e instanceof ApiError ? e.message : e instanceof TypeError ? "Unable to connect to the server. Please check your network connection." : fallback;

/**
 * Access token còn hạn (tự refresh nếu cần) — cho các kênh không đi qua `request`,
 * như WebSocket của console, nơi token được gửi trong frame đầu tiên.
 */
export async function freshToken(): Promise<string | null> {
  if (getRefreshToken() && isExpired(getToken())) await tryRefresh();
  return getToken();
}
