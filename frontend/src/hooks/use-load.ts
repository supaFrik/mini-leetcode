"use client";
import { useEffect, useState } from "react";
import { errorMessage } from "@/lib/api";

export type Load<T> = { state: "loading" } | { state: "ok"; data: T } | { state: "error"; message: string };

/** Tải dữ liệu theo deps; không bao giờ kẹt ở "đang tải". */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]): Load<T> {
  const [load, setLoad] = useState<Load<T>>({ state: "loading" });
  useEffect(() => {
    let off = false;
    setLoad({ state: "loading" });
    fn()
      .then((data) => !off && setLoad({ state: "ok", data }))
      .catch((e) => !off && setLoad({ state: "error", message: errorMessage(e) }));
    return () => {
      off = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return load;
}
