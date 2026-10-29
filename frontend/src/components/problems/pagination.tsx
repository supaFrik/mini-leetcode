import { cn } from "@/lib/utils";

export function pageList(current: number, total: number): (number | "…")[] {
  const keep = Array.from(new Set([1, total, current - 1, current, current + 1]))
    .filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
  const out: (number | "…")[] = [];
  keep.forEach((p, i) => { if (i > 0 && p - keep[i - 1] > 1) out.push("…"); out.push(p); });
  return out;
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  const btn = "h-7 min-w-7 rounded px-2 text-xs disabled:opacity-40";
  return (
    <div className="flex items-center gap-1">
      <button className={cn(btn, "text-muted-foreground")} disabled={page <= 1} onClick={() => onChange(page - 1)}>Prev</button>
      {pageList(page, totalPages).map((p, i) =>
        p === "…" ? <span key={`e${i}`} className="px-1 text-muted-foreground">…</span> : (
          <button key={p} onClick={() => onChange(p)} aria-current={p === page ? "page" : undefined}
            className={cn(btn, p === page ? "bg-primary text-primary-foreground" : "bg-secondary hover:bg-accent")}>{p}</button>
        ))}
      <button className={cn(btn, "bg-secondary hover:bg-accent")} disabled={page >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  );
}
