import { cn } from "@/lib/utils";

export function pageList(current: number, total: number): (number | "…")[] {
  const keep = Array.from(new Set([1, total, current - 1, current, current + 1]))
    .filter((p) => p >= 1 && p <= total)
    .sort((a, b) => a - b);
  const out: (number | "…")[] = [];
  keep.forEach((p, i) => {
    if (i > 0 && p - keep[i - 1] > 1) out.push("…");
    out.push(p);
  });
  return out;
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  const btn = "inline-flex h-8 min-w-8 items-center justify-center rounded-sm px-2 text-body-md transition-colors disabled:opacity-40";
  return (
    <div className="flex items-center gap-1">
      <button className={cn(btn, "text-body hover:bg-hairline-soft hover:text-ink")} disabled={page <= 1} onClick={() => onChange(page - 1)}>
        Prev
      </button>
      {pageList(page, totalPages).map((p, i) =>
        p === "…" ? (
          <span key={`e${i}`} className="px-1 text-mute">
            …
          </span>
        ) : (
          <button
            key={p}
            onClick={() => onChange(p)}
            aria-current={p === page ? "page" : undefined}
            className={cn(
              btn,
              p === page ? "bg-ink text-white" : "text-body hover:bg-hairline-soft hover:text-ink"
            )}
          >
            {p}
          </button>
        )
      )}
      <button
        className={cn(btn, "text-body hover:bg-hairline-soft hover:text-ink")}
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
