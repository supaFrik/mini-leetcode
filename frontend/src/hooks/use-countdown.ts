"use client";
import { useEffect, useState } from "react";

export function useCountdown(endTimeMs: number, serverOffsetMs = 0) {
  const [left, setLeft] = useState<number | null>(null);   // null lúc đầu để tránh lệch hydration
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, endTimeMs - (Date.now() + serverOffsetMs)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endTimeMs, serverOffsetMs]);
  return left;
}

export function formatMs(ms: number) {
  const s = Math.floor(ms / 1000), pad = (n: number) => String(n).padStart(2, "0");
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${pad(m)}:${pad(s % 60)}`;
}