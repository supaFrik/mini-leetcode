import { getToken } from "./auth";
import type { ApiResponse, ProblemDetail } from "@/types/api";

export const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK === "true";

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail | null) {
    super(problem?.detail ?? `HTTP ${status}`);
  }
}

export async function request<T>(path: string, init: RequestInit = {}) {
  const token = getToken();
  const res = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && typeof window !== "undefined") window.location.href = "/login";
    throw new ApiError(res.status, body);
  }
  // Lấy status từ HTTP (res.status), KHÔNG lấy từ body.statusCode:
  // spec ghi HTTP 202 nhưng body.statusCode lại là 200.
  return { data: (body as ApiResponse<T>).data, status: res.status, headers: res.headers };
}

export async function api<T>(path: string, init?: RequestInit) {
  return (await request<T>(path, init)).data;
}