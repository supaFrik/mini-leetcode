"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, api } from "@/lib/api";
import type { ProctoringEventType, ProctoringResult } from "@/types/api";

export function useProctoring(contestId: number | undefined, enabled: boolean) {
  const [state, setState] = useState({ count: 0, max: 3, disqualified: false });
  const [warning, setWarning] = useState<{ count: number; max: number } | null>(null);
  const away = useRef(false);
  const prevCount = useRef(0);
  const lastPaste = useRef(0);

  const send = useCallback(async (eventType: ProctoringEventType) => {
    if (!contestId) return;
    try {
      const r = await api<ProctoringResult>(`/api/v1/contests/${contestId}/proctoring/events`, {
        method: "POST",
        keepalive: true,                                   // vẫn gửi được khi tab đang bị ẩn
        body: JSON.stringify({ eventType, clientTimestamp: Date.now() }),
      });
      setState({ count: r.violationCount, max: r.maxViolationsAllowed, disqualified: r.isDisqualified });
      // Server là bên quyết định: chỉ cảnh báo khi số vi phạm THỰC SỰ tăng
      if (!r.isDisqualified && r.violationCount > prevCount.current)
        setWarning({ count: r.violationCount, max: r.maxViolationsAllowed });
      prevCount.current = r.violationCount;
    } catch (e) {
      // Tài liệu có 2 cách mô tả (200 + isDisqualified, hoặc 403) → xử lý cả hai
      if (e instanceof ApiError && e.status === 403 && /disqualified/i.test(e.message))
        setState((s) => ({ ...s, disqualified: true }));
    }
  }, [contestId]);

  useEffect(() => {
    if (!enabled) return;                                   // Practice: KHÔNG gắn listener nào
    const goAway = () => { if (away.current) return; away.current = true; void send("WINDOW_BLUR"); };
    const comeBack = () => { if (!away.current) return; away.current = false; void send("WINDOW_FOCUS"); };
    const onVis = () => (document.hidden || !document.hasFocus() ? goAway() : comeBack());

    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("blur", goAway);
    window.addEventListener("focus", comeBack);
    return () => {
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener("blur", goAway);
      window.removeEventListener("focus", comeBack);
    };
  }, [enabled, send]);

  // Gọi hàm này từ CodeEditor.onPasteAttempt
  const reportPaste = useCallback(() => {
    if (Date.now() - lastPaste.current < 500) return;       // chống gửi trùng 2 lần cho cùng 1 hành động
    lastPaste.current = Date.now();
    void send("CLIPBOARD_PASTE");
  }, [send]);

  return { ...state, warning, dismissWarning: () => setWarning(null), reportPaste };
}