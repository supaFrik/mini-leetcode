"use client";
import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { fetchAdminProblems } from "@/lib/services";
import type { ProblemFull } from "@/types/api";

/** Tìm đề PUBLISHED theo tên / slug (cả đề lẻ lẫn đề trong khoá học) để gắn làm stage hoặc bài code. */
export function ProblemPicker({ exclude, onPick, disabled }: { exclude: Set<number>; onPick: (p: ProblemFull) => void; disabled?: boolean }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<ProblemFull[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!q.trim()) {
      setResults([]);
      return;
    }
    let off = false;
    const t = setTimeout(() => {
      setBusy(true);
      fetchAdminProblems({ page: 1, q: q.trim(), difficulty: "", status: "PUBLISHED", tags: [], scope: "ALL" })
        .then((page) => !off && setResults(page.content))
        .catch(() => !off && setResults([]))
        .finally(() => !off && setBusy(false));
    }, 250);
    return () => {
      off = true;
      clearTimeout(t);
    };
  }, [q]);

  const visible = results.filter((p) => !exclude.has(p.id)).slice(0, 8);
  return (
    <div className="space-y-1">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" aria-hidden="true" />
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Add a published problem — search by title or slug"
          className="pl-8" aria-label="Search published problems" disabled={disabled} />
      </div>
      {q.trim() && (
        <ul className="rounded-sm border border-border bg-card" aria-label="Problem search results">
          {busy && <li className="px-3 py-2 text-body-sm text-mute">Searching…</li>}
          {!busy && visible.length === 0 && <li className="px-3 py-2 text-body-sm text-mute">No published problem matches.</li>}
          {visible.map((p) => (
            <li key={p.id}>
              <button type="button" className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-hairline-soft"
                onClick={() => { onPick(p); setQ(""); }}>
                <span><span className="text-ink">{p.title}</span> <span className="font-mono text-body-sm text-mute">{p.slug}</span></span>
                <DifficultyBadge value={p.difficulty} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
