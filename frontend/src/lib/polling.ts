import type { Submission } from "@/types/api";

// Lần 1: 1.0s, lần 2: 2.0s, lần 3: 3.5s, sau đó 5s ± 500ms (jitter) – theo TECHNICAL_SPECIFICATION §2.5
const SCHEDULE = [1000, 2000, 3500];
export const nextDelay = (attempt: number) =>
  attempt < SCHEDULE.length ? SCHEDULE[attempt] : 5000 + (Math.random() * 1000 - 500);

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const t = setTimeout(resolve, ms);
    signal.addEventListener("abort", () => { clearTimeout(t); resolve(); }, { once: true });
  });

export interface PollFetch { data: Submission; retryAfterMs: number }

export async function pollSubmission(
  fetcher: (signal: AbortSignal) => Promise<PollFetch>,
  { signal, onUpdate, maxMs = 120_000 }: { signal: AbortSignal; onUpdate: (s: Submission) => void; maxMs?: number },
): Promise<Submission | null> {
  const started = Date.now();
  let attempt = 0;
  let retryAfterMs = 0;
  while (!signal.aborted && Date.now() - started < maxMs) {
    await sleep(Math.max(nextDelay(attempt++), retryAfterMs), signal);
    if (signal.aborted) return null;
    try {
      const { data, retryAfterMs: ra } = await fetcher(signal);
      onUpdate(data);
      if (data.status === "COMPLETED" || data.status === "FAILED") return data;
      retryAfterMs = ra; // header Retry-After: server báo đang quá tải
    } catch (e) {
      if ((e as Error).name === "AbortError") return null;
      // rớt mạng hoặc lỗi tạm thời: bỏ qua, vòng sau thử lại
    }
  }
  return null;
}
