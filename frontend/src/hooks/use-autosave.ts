"use client";
import { useEffect, useRef, useState } from "react";

type Status = "saved" | "saving" | "offline";

export function useAutosave({ storageKey, value, sync, intervalMs = 15_000 }: {
  storageKey: string; value: string; sync?: (v: string) => Promise<void>; intervalMs?: number;
}) {
  const [status, setStatus] = useState<Status>("saved");
  const latest = useRef(value); latest.current = value;
  const dirty = useRef(false);
  const first = useRef(true);

  // (1) Lưu cục bộ ngay khi gõ (debounce 400ms) → mất mạng cũng không mất bài
  useEffect(() => {
    if (first.current) { first.current = false; return; }   // bỏ lần render đầu
    dirty.current = true;
    const t = setTimeout(() => {
      try { localStorage.setItem(storageKey, JSON.stringify({ value, savedAt: Date.now() })); } catch {}
    }, 400);
    return () => clearTimeout(t);
  }, [value, storageKey]);

  // (2) Đồng bộ lên server mỗi 15 giây, chỉ khi có thay đổi
  useEffect(() => {
    if (!sync) return;
    const flush = async () => {
      if (!dirty.current) return;
      setStatus("saving");
      try { await sync(latest.current); dirty.current = false; setStatus("saved"); }
      catch { setStatus("offline"); }                       // lỗi → giữ dirty để lần sau thử lại
    };
    const id = setInterval(flush, intervalMs);
    window.addEventListener("online", flush);               // có mạng lại thì đẩy lên ngay
    return () => { clearInterval(id); window.removeEventListener("online", flush); };
  }, [sync, intervalMs]);

  return status;   // hiển thị: "Auto-saved" / "Saving…" / banner vàng "Mất kết nối mạng..."
}