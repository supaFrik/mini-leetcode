import { clearTokens, getRefreshToken, getToken, setTokens } from "./auth";
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

// Refresh token xoay vòng (RFC 6819): chỉ cho phép MỘT lần refresh tại một thời điểm,
// nếu không 2 request song song sẽ làm server tưởng token bị đánh cắp và huỷ cả họ token.
let refreshing: Promise<boolean> | null = null;
function tryRefresh(): Promise<boolean> {
  const rt = getRefreshToken();
  if (!rt) return Promise.resolve(false);
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: rt }),
        });
        if (!res.ok) return false;
        const body = await res.json();
        const d = body?.data ?? body;
        if (!d?.accessToken) return false;
        setTokens(d.accessToken, d.refreshToken);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => { refreshing = null; }, 0);
      }
    })();
  }
  return refreshing;
}

export async function request<T>(path: string, init: RequestInit = {}, canRetry = true) {
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

  const isAuthCall = path.startsWith("/api/v1/auth/");
  if (res.status === 401 && canRetry && !isAuthCall && (await tryRefresh())) {
    return request<T>(path, init, false);
  }
  if (!res.ok) {
    if (res.status === 401 && !isAuthCall && typeof window !== "undefined") {
      clearTokens();
      window.location.href = "/login";
    }
    throw new ApiError(res.status, body as ProblemDetail | null);
  }
  return { data: (body as ApiResponse<T>)?.data as T, status: res.status, headers: res.headers };
}

export async function api<T>(path: string, init?: RequestInit) {
  return (await request<T>(path, init)).data;
}

export const errorMessage = (e: unknown, fallback = "Đã có lỗi xảy ra. Vui lòng thử lại.") =>
  e instanceof ApiError ? e.message : e instanceof TypeError ? "Không thể kết nối máy chủ. Hãy kiểm tra đường truyền." : fallback;
