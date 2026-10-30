"use client";

import { toast as toastManager } from "@/components/ui/toast";

/**
 * Legacy toast helper — forwards to the shadcn/Base UI toast manager.
 *
 * Existing call sites (`toast("Draft saved.", "success")`) keep working
 * unchanged. New code should prefer the manager API directly:
 *
 * ```tsx
 * import { toast } from "@/components/ui/toast";
 * toast.add({ title: "Event created", description: "...", type: "success" });
 * ```
 */
export type ToastKind = "info" | "success" | "error";

export function toast(
  message: string,
  kind: ToastKind = "info",
  ms = kind === "error" ? 7000 : 3500
) {
  return toastManager.add({ title: message, type: kind, timeout: ms });
}

export function dismissToast(id: string) {
  toastManager.close(id);
}
