"use client";
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { dismissToast, subscribeToasts, type ToastItem } from "@/lib/toast";

export function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);
  useEffect(() => subscribeToasts(setItems), []);
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
      {items.map((t) => (
        <div key={t.id} role={t.kind === "error" ? "alert" : "status"}
          className={cn("pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg backdrop-blur",
            t.kind === "error" && "border-destructive/50 bg-destructive/15 text-destructive",
            t.kind === "success" && "border-primary/40 bg-card text-foreground",
            t.kind === "info" && "bg-card text-foreground")}>
          <span className="flex-1">{t.message}</span>
          <button aria-label="Đóng thông báo" onClick={() => dismissToast(t.id)} className="opacity-70 hover:opacity-100"><X className="h-4 w-4" /></button>
        </div>
      ))}
    </div>
  );
}
