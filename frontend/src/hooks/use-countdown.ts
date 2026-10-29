"use client";
import { useEffect, useState } from "react";
import { getServerOffset } from "@/lib/api";

// Đếm ngược theo GIỜ SERVER (giờ máy + độ lệch), không tin đồng hồ máy thí sinh.
// Trả về null ở lần render đầu để tránh lệch hydration.
export function useCountdown(endTimeMs: number | null) {
  const [left, setLeft] = useState<number | null>(null);
  useEffect(() => {
    if (!endTimeMs) { setLeft(null); return; }
    const tick = () => setLeft(Math.max(0, endTimeMs - (Date.now() + getServerOffset())));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endTimeMs]);
  return left;
}
