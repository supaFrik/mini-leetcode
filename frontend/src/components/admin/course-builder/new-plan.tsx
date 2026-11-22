"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Loader2, Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { createStudyPlan } from "@/lib/services";

const field = "block space-y-1 text-body-sm text-mute";

/** Hộp "New study plan": tiêu đề + số session; tạo plan nháp với các session trống rồi mở Course Builder. */
export function NewPlanDialog({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [count, setCount] = useState(3);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const p = await createStudyPlan({ title: title.trim(),
        sessions: Array.from({ length: count }, (_, i) => ({ title: `Session ${i + 1}`, entries: [] })) });
      router.push(`/admin/study-plans/${p.slug}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/20 p-4" role="dialog" aria-modal="true" aria-label="New study plan"
      onKeyDown={(e) => e.key === "Escape" && onClose()}>
      <form className="w-full max-w-md space-y-4 rounded-sm border border-border bg-background p-5 shadow-lg"
        onSubmit={(e) => { e.preventDefault(); if (title.trim()) void create(); }}>
        <div className="flex items-center justify-between">
          <h2 className="text-heading-md text-ink">New study plan</h2>
          <Button type="button" size="icon-sm" variant="ghost" aria-label="Close" onClick={onClose}><X className="h-4 w-4" /></Button>
        </div>
        <label className={field}>Title
          <Input autoFocus required value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. OOP in Java: Mini Bank" />
        </label>
        <label className={field}>Number of sessions
          <Input type="number" min={1} max={50} value={count}
            onChange={(e) => setCount(Math.min(50, Math.max(1, Number(e.target.value) || 1)))} />
        </label>
        {error && <p role="alert" className="text-body-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy || !title.trim()}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}Create</Button>
        </div>
      </form>
    </div>
  );
}

/** /admin/study-plans/new: mở hộp tạo plan; đóng thì về danh sách. */
export function NewStudyPlan() {
  const router = useRouter();
  return <NewPlanDialog onClose={() => router.push("/admin/study-plans")} />;
}
