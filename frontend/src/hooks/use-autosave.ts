"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import type { Draft } from "@/types/api";

export type SaveStatus = "saved" | "saving" | "offline";
export interface RemoteDraft { load: () => Promise<Draft | null>; save: (value: string) => Promise<void> }

const readLocal = (key: string): Draft | null => { try { const v = localStorage.getItem(key); return v ? (JSON.parse(v) as Draft) : null; } catch { return null; } };
const writeLocal = (key: string, value: string) => { try { localStorage.setItem(key, JSON.stringify({ value, savedAt: Date.now() })); } catch { /* private mode: bỏ qua */ } };

/**
 * 2 tầng lưu:
 *  1) LocalStorage ngay khi gõ (debounce 400ms) → rớt mạng cũng không mất bài.
 *  2) Server mỗi 15 giây, chỉ khi có thay đổi (nếu truyền `remote`).
 * Khi đổi `storageKey` (đổi bài / đổi ngôn ngữ) hook khôi phục bản nháp MỚI NHẤT giữa local và server.
 */
export function useAutosave({ storageKey, value, setValue, remote, intervalMs = 15_000 }: {
  storageKey: string; value: string; setValue: (v: string) => void; remote?: RemoteDraft; intervalMs?: number;
}) {
  const [ready, setReady] = useState(false);
  const [status, setStatus] = useState<SaveStatus>("saved");
  const latest = useRef(value); latest.current = value;
  const setValueRef = useRef(setValue); setValueRef.current = setValue;
  const remoteRef = useRef(remote); remoteRef.current = remote;
  const readyKey = useRef<string | null>(null); // key đã khôi phục xong
  const skipOnce = useRef(false);               // bỏ qua đúng 1 lần đổi value do việc khôi phục gây ra
  const dirty = useRef(false);

  // (0) Khôi phục khi đổi key
  useEffect(() => {
    let cancelled = false;
    readyKey.current = null; dirty.current = false; setReady(false);
    (async () => {
      await Promise.resolve();
      let best = readLocal(storageKey);
      const r = remoteRef.current;
      if (r) {
        try { const s = await r.load(); if (s && (!best || s.savedAt > best.savedAt)) best = s; }
        catch { if (!cancelled) setStatus("offline"); }
      }
      if (cancelled) return;
      skipOnce.current = true;
      if (best && best.value !== latest.current) setValueRef.current(best.value);
      readyKey.current = storageKey;
      setReady(true);
    })();
    return () => { cancelled = true; };
  }, [storageKey]);

  // (1) Lưu cục bộ khi gõ
  useEffect(() => {
    if (readyKey.current !== storageKey) return;
    if (skipOnce.current) { skipOnce.current = false; return; }
    dirty.current = true;
    const t = setTimeout(() => writeLocal(storageKey, value), 400);
    return () => clearTimeout(t);
  }, [value, ready, storageKey]);

  // (2) Đồng bộ server
  const flush = useCallback(async () => {
    const r = remoteRef.current;
    if (!r || !dirty.current || readyKey.current !== storageKey) return;
    const snapshot = latest.current;
    dirty.current = false; // nếu người dùng gõ tiếp trong lúc gửi, cờ sẽ bật lại
    setStatus("saving");
    try { await r.save(snapshot); setStatus("saved"); }
    catch { dirty.current = true; setStatus("offline"); }
  }, [storageKey]);

  const hasRemote = !!remote;
  useEffect(() => {
    if (!hasRemote) return;
    const id = setInterval(flush, intervalMs);
    window.addEventListener("online", flush); // có mạng lại thì đẩy lên ngay
    return () => { clearInterval(id); window.removeEventListener("online", flush); };
  }, [hasRemote, flush, intervalMs]);

  const saveNow = useCallback(() => { writeLocal(storageKey, latest.current); dirty.current = true; return flush(); }, [storageKey, flush]);
  return { ready, status, saveNow };
}
