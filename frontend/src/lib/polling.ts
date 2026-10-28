import { request } from "./api";
import type { Submission } from "@/types/api";

const SCHEDULE = [1000, 2000, 3500];                 // lần 1, 2, 3
const nextDelay = (attempt: number) =>
  attempt < SCHEDULE.length ? SCHEDULE[attempt] : 5000 + (Math.random() * 1000 - 500);  // 5s ± 500ms

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const t = setTimeout(resolve, ms);
    signal.addEventListener("abort", () => { clearTimeout(t); resolve(); }, { once: true });
  });

export async function pollSubmission(
  id: number,
  { signal, onUpdate, maxMs = 120_000 }: { signal: AbortSignal; onUpdate: (s: Submission) => void; maxMs?: number },
) {
  const started = Date.now();
  let attempt = 0;
  let retryAfterMs = 0;

  while (!signal.aborted && Date.now() - started < maxMs) {
    await sleep(Math.max(nextDelay(attempt++), retryAfterMs), signal);
    if (signal.aborted) return null;
    try {
      const { data, headers } = await request<Submission>(`/api/v1/submissions/${id}`, { signal });
      onUpdate(data);
      if (data.status === "COMPLETED" || data.status === "FAILED") return data;
      retryAfterMs = Number(headers.get("Retry-After") ?? 0) * 1000;   // server báo "đang quá tải, chờ thêm"
    } catch {
      /* rớt mạng: bỏ qua, vòng sau thử lại */
    }
  }
  return null; // quá thời gian → UI hiện "chấm lâu hơn dự kiến"
}