export type ToastKind = "info" | "success" | "error";
export interface ToastItem { id: number; kind: ToastKind; message: string }

let items: ToastItem[] = [];
let seq = 0;
const listeners = new Set<(t: ToastItem[]) => void>();
const emit = () => listeners.forEach((l) => l(items));

export function dismissToast(id: number) { items = items.filter((t) => t.id !== id); emit(); }
export function toast(message: string, kind: ToastKind = "info", ms = kind === "error" ? 7000 : 3500) {
  const id = ++seq;
  items = [...items.slice(-3), { id, kind, message }];
  emit();
  setTimeout(() => dismissToast(id), ms);
}
export function subscribeToasts(l: (t: ToastItem[]) => void) {
  listeners.add(l);
  l(items);
  return () => { listeners.delete(l); };
}
