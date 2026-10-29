"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { postProctoringEvent } from "@/lib/services";
import type { ProctoringEventType, ProctoringLogItem } from "@/types/api";

/**
 * Chỉ BÁO sự kiện lên server. Client KHÔNG tự đếm vi phạm hay tính ΔT:
 * server là thẩm quyền duy nhất (Server-Side Authority).
 *
 * "Rời khỏi giao diện thi" gồm 3 nguồn, dùng chung 1 cờ `away` để mỗi lần rời chỉ gửi 1 BLUR:
 *   1) đổi tab / thu nhỏ (visibilitychange)    2) mất focus cửa sổ (blur)
 *   3) thoát toàn màn hình bằng Esc (fullscreenchange)  → gửi BLUR; khi vào lại gửi FOCUS,
 *      server tính ΔT = thời gian ở ngoài, nếu ≥ 1.5s thì tính 1 lần vi phạm.
 */
export function useProctoring({ contestId, enabled, initialCount, max, initialDisqualified }: {
  contestId?: number; enabled: boolean; initialCount: number; max: number; initialDisqualified: boolean;
}) {
  const [count, setCount] = useState(initialCount);
  const [maxViolations, setMax] = useState(max);
  const [disqualified, setDisqualified] = useState(initialDisqualified);
  const [warning, setWarning] = useState<{ count: number; max: number } | null>(null);
  const [fullscreenLost, setFullscreenLost] = useState(false);
  const [log, setLog] = useState<ProctoringLogItem[]>([]);
  const away = useRef(false);
  const prevCount = useRef(initialCount);
  const lastPaste = useRef(0);
  const queue = useRef<ProctoringEventType[]>([]);

  const send = useCallback(async (type: ProctoringEventType) => {
    if (!contestId) return;
    try {
      const r = await postProctoringEvent(contestId, type);
      setCount(r.violationCount); setMax(r.maxViolationsAllowed);
      setLog((l) => [{ at: Date.now(), type, message: r.message, violationCount: r.violationCount }, ...l].slice(0, 30));
      if (r.isDisqualified) setDisqualified(true);
      // Chỉ cảnh báo khi số vi phạm THỰC SỰ tăng (click nhầm < 1.5s thì server không tăng)
      else if (r.violationCount > prevCount.current) setWarning({ count: r.violationCount, max: r.maxViolationsAllowed });
      prevCount.current = r.violationCount;
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.status === 403 && /disqualified/i.test(e.message)) setDisqualified(true); // spec có chỗ ghi 403
      } else {
        queue.current.push(type); // rớt mạng: gửi lại khi có mạng
      }
    }
  }, [contestId]);

  useEffect(() => {
    if (!enabled) return; // Practice: KHÔNG gắn listener nào
    const goAway = () => { if (away.current) return; away.current = true; void send("WINDOW_BLUR"); };
    const comeBack = () => { if (!away.current) return; away.current = false; void send("WINDOW_FOCUS"); };
    const onVis = () => (document.hidden || !document.hasFocus() ? goAway() : comeBack());
    // Trình duyệt không hỗ trợ fullscreen (vd iOS Safari) thì bỏ qua yêu cầu này
    const fsRequired = document.fullscreenEnabled;
    const onFs = () => {
      if (!fsRequired) return;
      if (document.fullscreenElement) { setFullscreenLost(false); comeBack(); }
      else { setFullscreenLost(true); goAway(); }
    };
    const onOnline = async () => { const q = queue.current.splice(0); for (const t of q) await send(t); };

    if (fsRequired && !document.fullscreenElement) setFullscreenLost(true); // chưa vào được fullscreen → yêu cầu vào
    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("blur", goAway);
    window.addEventListener("focus", comeBack);
    document.addEventListener("fullscreenchange", onFs);
    window.addEventListener("online", onOnline);
    return () => {
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener("blur", goAway);
      window.removeEventListener("focus", comeBack);
      document.removeEventListener("fullscreenchange", onFs);
      window.removeEventListener("online", onOnline);
    };
  }, [enabled, send]);

  // Server phạt NGAY mỗi event paste → chặn gửi trùng 2 lần cho cùng 1 hành động (phím tắt + sự kiện paste)
  const reportPaste = useCallback(() => {
    if (Date.now() - lastPaste.current < 500) return;
    lastPaste.current = Date.now();
    void send("CLIPBOARD_PASTE");
  }, [send]);

  const enterFullscreen = useCallback(async () => {
    try { await document.documentElement.requestFullscreen(); } catch { /* trình duyệt từ chối: giữ lớp phủ */ }
  }, []);

  return {
    count, max: maxViolations, disqualified, warning, log, fullscreenLost: enabled && fullscreenLost,
    dismissWarning: () => setWarning(null), markDisqualified: () => setDisqualified(true), reportPaste, enterFullscreen,
  };
}
