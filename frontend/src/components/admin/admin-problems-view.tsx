"use client";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ExternalLink, Plus, Search, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { Pagination } from "@/components/problems/pagination";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useUser } from "@/hooks/use-user";
import { AdminNav } from "./admin-nav";
import { PROBLEM_STATUS_TONE } from "./status";
import { deleteProblem, fetchAdminProblems } from "@/lib/services";
import type { Difficulty, Page, ProblemFull, ProblemLifecycle, ProblemScope, ProblemType } from "@/types/api";

const LIFECYCLES: { value: ProblemLifecycle; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "REVIEWING", label: "Reviewing" },
  { value: "PUBLISHED", label: "Published" },
  { value: "ARCHIVED", label: "Archived" },
];
const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];
const SCOPES: { value: Exclude<ProblemScope, "ALL">; label: string }[] = [
  { value: "STANDALONE", label: "Standalone" },
  { value: "COURSE", label: "In courses" },
];
const selectClass = "h-10 rounded-sm border border-border bg-card px-2.5 text-body-md text-ink";

/**
 * /admin/problems: danh sách đề. Mặc định chỉ đề lẻ; đề nằm trong study plan / chủ đề xem ở "In courses"
 * và soạn ngay trong Course Builder. Bấm một dòng để mở trang soạn /admin/problems/[id].
 */
export function AdminProblemsView() {
  const ask = useConfirm();
  const [res, setRes] = useState<Page<ProblemFull> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  useEffect(() => {
    const t = setTimeout(() => { setQ(text.trim()); setPage(1); }, 250);
    return () => clearTimeout(t);
  }, [text]);
  const [scope, setScope] = useState<Exclude<ProblemScope, "ALL">>("STANDALONE");
  const [status, setStatus] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [type, setType] = useState<ProblemType | "">("");
  const [pending, setPending] = useState<number | null>(null);
  const { user } = useUser();
  // Server chỉ cho ADMIN xoá (DELETE /api/v1/problems/** hasRole ADMIN) — INSTRUCTOR không thấy nút.
  const canDelete = user?.role === "ADMIN";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRes(await fetchAdminProblems({ page, q, status, difficulty, tags: [], type: type || undefined, scope }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [page, q, status, difficulty, type, scope]);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (p: ProblemFull) => {
    if (!(await ask({
      title: `Delete “${p.title}”?`,
      description: "Its test cases, grading rules and reference solution are deleted too. Problems with submissions can only be archived.",
      action: "Delete problem", destructive: true,
    }))) return;
    setPending(p.id);
    try {
      await deleteProblem(p.id);
      toast("Problem deleted.", "success");
      await load();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setPending(null);
    }
  };

  const rows = useMemo(() => res?.content ?? [], [res]);
  const filter = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(1); };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="flex flex-wrap items-center gap-2">
        <div className="inline-flex rounded-sm border border-border bg-card p-0.5" role="group" aria-label="Problem scope">
          {SCOPES.map((s) => (
            <button key={s.value} type="button" aria-pressed={scope === s.value} onClick={() => filter(setScope)(s.value)}
              className={cn("h-8 rounded-[5px] px-3 text-body-sm text-mute transition-colors hover:text-ink",
                scope === s.value && "bg-ink text-white hover:text-white")}>
              {s.label}
            </button>
          ))}
        </div>
        <div className="relative min-w-0 flex-1 basis-56">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Search" aria-label="Search problems"
            className="h-10 rounded-sm bg-card pl-9 text-body-md text-ink" />
        </div>
        <select value={status} onChange={(e) => filter(setStatus)(e.target.value)} aria-label="Filter by lifecycle status" className={selectClass}>
          <option value="">Any status</option>
          {LIFECYCLES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
        <select value={difficulty} onChange={(e) => filter(setDifficulty)(e.target.value)} aria-label="Filter by difficulty" className={selectClass}>
          <option value="">Any difficulty</option>
          {DIFFICULTIES.map((d) => <option key={d} value={d}>{d}</option>)}
        </select>
        <select value={type} onChange={(e) => filter(setType)(e.target.value as ProblemType | "")} aria-label="Filter by problem type" className={selectClass}>
          <option value="">Any type</option>
          <option value="ALGORITHMIC">DSA</option>
          <option value="OOP_GRINDING">OOP</option>
        </select>
        <Button asChild>
          <Link href="/admin/problems/new"><Plus className="h-4 w-4" />New problem</Link>
        </Button>
      </div>

      {error && (
        <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">{error}</p>
      )}

      <section aria-label="Problem administration" className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="hidden items-center gap-3 px-5 py-3 text-[11px] font-medium uppercase tracking-wider text-mute md:grid md:grid-cols-[56px_minmax(0,1fr)_104px_96px_56px_72px]">
          <span>ID</span><span>Title</span><span>Status</span><span>Difficulty</span><span>Tests</span><span />
        </div>

        {loading && !res && (
          <div className="space-y-2 p-5">
            {Array.from({ length: 5 }).map((_, i) => <div key={i} className="h-9 animate-pulse rounded bg-hairline-soft" />)}
          </div>
        )}

        {rows.map((p) => {
          const s = (p.status ?? "DRAFT") as ProblemLifecycle;
          return (
            <div key={p.id}
              className="grid grid-cols-1 items-center gap-2 border-t border-border px-5 py-3 transition-colors hover:bg-hairline-soft/60 md:grid-cols-[56px_minmax(0,1fr)_104px_96px_56px_72px] md:gap-3">
              <span className="font-mono text-body-sm text-mute">{p.id}</span>
              <span className="min-w-0">
                <span className="flex min-w-0 items-baseline gap-2">
                <Link href={`/admin/problems/${p.id}`} className="min-w-0 truncate text-body-md text-ink hover:text-link">
                  {p.title}</Link>
                  <span className="shrink-0 font-mono text-[10px] font-semibold text-mute">{p.type === "OOP_GRINDING" ? "OOP" : "DSA"}</span>
                </span>
                {p.tags.length > 0 && <span className="mt-0.5 block truncate text-body-sm text-mute">{p.tags.slice(0, 4).join(" · ")}</span>}
              </span>
              <span><Badge variant={PROBLEM_STATUS_TONE[s]} className="font-mono text-[11px]">{s}</Badge></span>
              <span><DifficultyBadge value={p.difficulty} /></span>
              <span className="font-mono text-body-sm text-mute">{p.testCaseCount ?? 0}</span>
              <span className="flex items-center gap-1 md:justify-end">
                <Button size="icon-sm" variant="ghost" asChild>
                  <Link href={`/problems/${p.slug}`} target="_blank" aria-label={`View ${p.title}`} title="View"><ExternalLink className="h-3.5 w-3.5" /></Link>
                </Button>
                {canDelete && (
                  <Button size="icon-sm" variant="ghost" disabled={pending === p.id} onClick={() => remove(p)}
                    aria-label={`Delete ${p.title}`} title="Delete" className="text-mute hover:text-destructive">
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                )}
              </span>
            </div>
          );
        })}

        {res && rows.length === 0 && (
          <div className="border-t border-border p-12 text-center">
            <p className="text-body-md text-mute">No problems match.</p>
            {(q || status || difficulty || type) && (
              <Button variant="outline" size="sm" className="mt-4" onClick={() => {
                setText(""); setStatus(""); setDifficulty(""); setType(""); setPage(1);
              }}>Clear filters</Button>
            )}
          </div>
        )}

        {res && res.totalPages > 1 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-hairline-soft px-5 py-3">
            <span className="font-mono text-[11px] text-mute">
              {res.number * res.size + 1}–{res.number * res.size + rows.length} of {res.totalElements}
            </span>
            <Pagination page={page} totalPages={res.totalPages} onChange={setPage} />
          </div>
        )}
      </section>
    </div>
  );
}
