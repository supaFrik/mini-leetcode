"use client";
import { useCallback, useEffect, useState } from "react";
import { Loader2, Plus, Trash2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { AdminTabs } from "@/components/admin/admin-tabs";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { toast } from "@/lib/toast";
import {
  createContest,
  deleteContest,
  fetchAdminProblems,
  fetchContests,
  updateContest,
  type ContestDraft,
} from "@/lib/services";
import type { ContestInfo, ProblemFull } from "@/types/api";

/** ISO UTC từ server -> giá trị cho <input type="datetime-local"> (giờ máy người dùng). */
function toLocalInput(iso: string): string {
  const ms = parseApiDate(iso);
  if (Number.isNaN(ms)) return "";
  const d = new Date(ms - new Date(ms).getTimezoneOffset() * 60000);
  return d.toISOString().slice(0, 16);
}
/** Giờ local của input -> ISO UTC không kèm Z (server lưu UTC dạng LocalDateTime). */
const toUtc = (local: string) => new Date(local).toISOString().slice(0, 19);

const hourFromNow = (h: number) => toLocalInput(new Date(Date.now() + h * 3600_000).toISOString());

function ContestEditor({ contest, onClose, onSaved }: { contest: ContestInfo | null; onClose: () => void; onSaved: () => void }) {
  const [title, setTitle] = useState(contest?.title ?? "");
  const [start, setStart] = useState(contest ? toLocalInput(contest.startTime) : hourFromNow(24));
  const [end, setEnd] = useState(contest ? toLocalInput(contest.endTime) : hourFromNow(26));
  const [status, setStatus] = useState<ContestInfo["status"]>(contest?.status ?? "SCHEDULED");
  const [policy, setPolicy] = useState<ContestInfo["scoringPolicy"]>(contest?.scoringPolicy ?? "ACM_ICPC");
  const [maxViolations, setMaxViolations] = useState(contest?.maxViolationsAllowed ?? 3);
  const [selected, setSelected] = useState<{ id: number; title: string }[]>(
    contest?.problems.map((p) => ({ id: p.id, title: p.title })) ?? []
  );
  const [search, setSearch] = useState("");
  const [options, setOptions] = useState<ProblemFull[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      fetchAdminProblems({ page: 1, q: search, difficulty: "", status: "", tags: [] })
        .then((r) => setOptions(r.content))
        .catch(() => setOptions([]));
    }, 250);
    return () => clearTimeout(t);
  }, [search]);

  const save = async () => {
    setBusy(true);
    setError(null);
    const draft: ContestDraft = {
      title,
      startTime: toUtc(start),
      endTime: toUtc(end),
      status,
      scoringPolicy: policy,
      maxViolationsAllowed: maxViolations,
      problemIds: selected.map((p) => p.id),
    };
    try {
      if (contest) await updateContest(contest.id, draft);
      else await createContest(draft);
      toast(contest ? "Contest updated." : "Contest created.", "success");
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const move = (i: number, d: -1 | 1) =>
    setSelected((s) => {
      const n = [...s];
      [n[i], n[i + d]] = [n[i + d], n[i]];
      return n;
    });

  const field = "h-9 w-full rounded-sm border border-border bg-card px-2.5 text-body-md text-ink";

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-ink/20" role="dialog" aria-modal="true" aria-label="Contest editor">
      <div className="flex h-full w-full max-w-2xl flex-col border-l border-border bg-background">
        <header className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
          <div>
            <p className="eyebrow">{contest ? "Edit contest" : "New contest"}</p>
            <h2 className="mt-0.5 text-heading-md text-ink">{title || "Untitled"}</h2>
          </div>
          <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close editor">
            <X className="h-4 w-4" />
          </Button>
        </header>

        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 py-5">
          <label className="block space-y-2">
            <span className="eyebrow block text-mute">Title</span>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} className="rounded-sm bg-card" />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block space-y-2">
              <span className="eyebrow block text-mute">Start (your local time)</span>
              <input type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} className={field} />
            </label>
            <label className="block space-y-2">
              <span className="eyebrow block text-mute">End (your local time)</span>
              <input type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} className={field} />
            </label>
            <label className="block space-y-2">
              <span className="eyebrow block text-mute">Status</span>
              <select value={status} onChange={(e) => setStatus(e.target.value as ContestInfo["status"])} className={field}>
                <option value="SCHEDULED">Scheduled</option>
                <option value="RUNNING">Running</option>
                <option value="COMPLETED">Completed</option>
              </select>
            </label>
            <label className="block space-y-2">
              <span className="eyebrow block text-mute">Scoring</span>
              <select value={policy} onChange={(e) => setPolicy(e.target.value as ContestInfo["scoringPolicy"])} className={field}>
                <option value="ACM_ICPC">ACM / ICPC</option>
                <option value="PARTIAL_IOI">Partial (IOI)</option>
              </select>
            </label>
            <label className="block space-y-2">
              <span className="eyebrow block text-mute">Max violations</span>
              <Input type="number" min={0} value={maxViolations} onChange={(e) => setMaxViolations(Number(e.target.value))} className="rounded-sm bg-card" />
            </label>
          </div>

          <div className="space-y-2">
            <span className="eyebrow block text-mute">Problems (labels follow this order)</span>
            <ol className="overflow-hidden rounded-sm border border-border bg-card">
              {selected.map((p, i) => (
                <li key={p.id} className="flex items-center gap-2 border-t border-border px-3 py-2 first:border-t-0">
                  <span className="w-6 font-mono text-body-sm text-mute">{i < 26 ? String.fromCharCode(65 + i) : `P${i + 1}`}</span>
                  <span className="min-w-0 flex-1 truncate text-body-md text-ink">{p.title}</span>
                  <Button size="sm" variant="ghost" className="h-7 px-2" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">↑</Button>
                  <Button size="sm" variant="ghost" className="h-7 px-2" disabled={i === selected.length - 1} onClick={() => move(i, 1)} aria-label="Move down">↓</Button>
                  <Button size="sm" variant="ghost" className="h-7 px-2 text-mute hover:text-destructive" onClick={() => setSelected((s) => s.filter((x) => x.id !== p.id))} aria-label={`Remove ${p.title}`}>
                    <X className="h-3.5 w-3.5" />
                  </Button>
                </li>
              ))}
              {selected.length === 0 && <li className="px-3 py-2 text-body-sm text-mute">No problems yet.</li>}
            </ol>
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search problems to add" aria-label="Search problems to add" className="rounded-sm bg-card" />
            <ul className="max-h-48 overflow-y-auto rounded-sm border border-border bg-card">
              {options.filter((o) => !selected.some((s) => s.id === o.id)).map((o) => (
                <li key={o.id}>
                  <button
                    type="button"
                    onClick={() => setSelected((s) => [...s, { id: o.id, title: o.title }])}
                    className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-body-sm hover:bg-hairline-soft"
                  >
                    <Plus className="h-3 w-3 text-mute" />
                    <span className="flex-1 truncate text-ink">{o.title}</span>
                    <span className="font-mono text-[10px] text-mute">{o.status}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {error && (
            <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
              {error}
            </p>
          )}
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-border px-5 py-3">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={save} disabled={busy || !title.trim() || !start || !end}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            {contest ? "Save" : "Create"}
          </Button>
        </footer>
      </div>
    </div>
  );
}

export function AdminContestsView() {
  const [rows, setRows] = useState<ContestInfo[] | null>(null);
  const [editing, setEditing] = useState<ContestInfo | null | "new">(null);

  const load = useCallback(async () => {
    try {
      setRows(await fetchContests());
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (c: ContestInfo) => {
    if (!window.confirm(`Delete contest "${c.title}"? Participants and proctoring logs are removed too.`)) return;
    try {
      await deleteContest(c.id);
      toast("Contest deleted.", "success");
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const fmt = (iso: string) => new Date(parseApiDate(iso)).toLocaleString();

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <AdminTabs current="contests" />
          <h1 className="mt-1 text-heading-lg text-ink">Contests</h1>
          <p className="mt-1 text-body-md text-mute">Create contests, set their schedule, rules and problem set.</p>
        </div>
        <Button onClick={() => setEditing("new")}>
          <Plus className="h-4 w-4" />
          New contest
        </Button>
      </header>

      <section aria-label="Contest administration" className="overflow-hidden rounded-xl border border-border bg-card">
        {rows?.map((c) => (
          <div key={c.id} className="flex flex-wrap items-center gap-3 border-t border-border px-5 py-3.5 first:border-t-0">
            <button onClick={() => setEditing(c)} className="min-w-0 flex-1 truncate text-left text-body-md text-ink hover:text-link">
              {c.title}
            </button>
            <Badge variant="outline" className="font-mono text-[11px]">{c.status}</Badge>
            <span className="font-mono text-body-sm text-mute">{fmt(c.startTime)} → {fmt(c.endTime)}</span>
            <span className="font-mono text-body-sm text-mute">{c.problems.length} problems</span>
            <Button size="icon-sm" variant="ghost" onClick={() => remove(c)} aria-label={`Delete ${c.title}`} className="text-mute hover:text-destructive">
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        ))}
        {rows?.length === 0 && <p className="p-12 text-center text-body-md text-mute">No contests yet.</p>}
        {!rows && <div className="m-5 h-9 animate-pulse rounded bg-hairline-soft" />}
      </section>

      {editing && (
        <ContestEditor
          contest={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}
    </div>
  );
}
