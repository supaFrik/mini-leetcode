"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Archive,
  Check,
  ChevronRight,
  FileText,
  Loader2,
  Plus,
  Search,
  Trash2,
  X,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { Pagination } from "@/components/problems/pagination";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { GradingPanel, ReferencePanel, SourceFilesEditor, TestCasesPanel } from "./problem-authoring";
import {
  createProblem,
  deleteProblem,
  fetchAdminProblems,
  fetchAuthoring,
  saveProblem,
  transitionProblem,
  type ProblemDraft,
} from "@/lib/services";
import type {
  Difficulty,
  Page,
  ProblemAuthoring,
  ProblemFull,
  ProblemLifecycle,
  ProblemType,
  SubmissionFile,
} from "@/types/api";

const LIFECYCLES: { value: ProblemLifecycle; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "REVIEWING", label: "Reviewing" },
  { value: "PUBLISHED", label: "Published" },
  { value: "ARCHIVED", label: "Archived" },
];

const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];

/** Cạnh hợp lệ của vòng đời — mirror đúng luật server. */
const EDGES: Record<ProblemLifecycle, ProblemLifecycle[]> = {
  DRAFT: ["REVIEWING", "ARCHIVED"],
  REVIEWING: ["DRAFT", "PUBLISHED"],
  PUBLISHED: ["ARCHIVED"],
  ARCHIVED: ["DRAFT"],
};

const STATUS_TONE: Record<ProblemLifecycle, "default" | "secondary" | "success" | "warning" | "outline"> = {
  DRAFT: "secondary",
  REVIEWING: "warning",
  PUBLISHED: "success",
  ARCHIVED: "outline",
};

const EMPTY_DRAFT: ProblemDraft = {
  title: "",
  description: "",
  difficulty: "EASY",
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  tags: [],
  type: "ALGORITHMIC",
  starterTemplates: null,
};

/* ------------------------------------------------------------------ *
 * Tag editor — the same normalisation the server applies, mirrored so the
 * UI never shows a tag the backend would silently rewrite.
 * ------------------------------------------------------------------ */

function normalizeTags(raw: string): string[] {
  const seen = new Map<string, string>();
  for (const part of raw.split(",")) {
    const t = part.trim();
    if (!t) continue;
    const key = t.toLowerCase();
    if (!seen.has(key)) seen.set(key, t);
  }
  return Array.from(seen.values());
}

function TagEditor({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState(value.join(", "));

  useEffect(() => {
    setText(value.join(", "));
  }, [value]);

  const commit = (next: string) => {
    setText(next);
    onChange(normalizeTags(next));
  };

  const over = value.filter((t) => t.length > 50);

  return (
    <div className="space-y-2">
      <label htmlFor="admin-tags" className="eyebrow block text-mute">
        Topics
      </label>
      <Input
        id="admin-tags"
        value={text}
        onChange={(e) => commit(e.target.value)}
        placeholder="Array, Hash Table"
        className="rounded-sm bg-card text-body-md text-ink"
        aria-describedby="admin-tags-help"
      />
      <p id="admin-tags-help" className="text-body-sm text-mute">
        Separate topics with commas. Duplicates are merged, case is preserved.
      </p>
      {over.length > 0 && (
        <p className="text-body-sm text-destructive" role="alert">
          {over.length} topic(s) exceed the 50-character limit.
        </p>
      )}
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {value.map((t) => (
            <span key={t} className="inline-flex items-center gap-1 rounded-full border border-border bg-card py-0.5 pl-2.5 pr-1 text-body-sm text-body">
              {t}
              <button
                type="button"
                aria-label={`Remove topic ${t}`}
                onClick={() => onChange(value.filter((x) => x !== t))}
                className="grid h-4 w-4 place-items-center rounded-full text-mute hover:bg-hairline-soft hover:text-ink"
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Editor panel — create and edit share one form.
 * ------------------------------------------------------------------ */

const DEFAULT_STARTER: SubmissionFile[] = [
  {
    path: "Main.java",
    content:
      "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        // TODO\n    }\n}\n",
  },
];

/** Starter template của bài OOP: lưu dạng chuỗi JSON [{path, content}] trong ProblemDraft. */
function StarterTemplateEditor({
  value,
  onChange,
}: {
  value: string | null | undefined;
  onChange: (v: string) => void;
}) {
  const files = useMemo<SubmissionFile[]>(() => {
    if (value) {
      try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      } catch {
        /* ignore parse error */
      }
    }
    return DEFAULT_STARTER;
  }, [value]);

  return (
    <SourceFilesEditor
      label="Starter templates (.java)"
      files={files}
      onChange={(next) => onChange(JSON.stringify(next))}
      rows={8}
    />
  );
}

function Editor({
  problem,
  onClose,
  onSaved,
}: {
  problem: ProblemFull | null;
  onClose: () => void;
  onSaved: (p: ProblemFull) => void;
}) {
  const [draft, setDraft] = useState<ProblemDraft>(EMPTY_DRAFT);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState("details");
  const [authoring, setAuthoring] = useState<ProblemAuthoring | null>(null);
  const [authoringError, setAuthoringError] = useState<string | null>(null);

  // Rule chấm + lời giải mẫu chỉ staff đọc được, tải riêng khi mở một bài đã tồn tại.
  useEffect(() => {
    setAuthoring(null);
    setAuthoringError(null);
    if (!problem) return;
    let live = true;
    fetchAuthoring(problem.id)
      .then((a) => live && setAuthoring(a))
      .catch((e) => live && setAuthoringError(errorMessage(e)));
    return () => {
      live = false;
    };
  }, [problem]);

  useEffect(() => {
    if (!problem) {
      setDraft(EMPTY_DRAFT);
      return;
    }
    setDraft({
      title: problem.title,
      description: problem.description,
      difficulty: problem.difficulty,
      timeLimitMs: problem.timeLimitMs,
      memoryLimitMb: problem.memoryLimitMb,
      tags: [...problem.tags],
      type: problem.type ?? "ALGORITHMIC",
      starterTemplates: problem.starterTemplates ?? null,
    });
  }, [problem]);

  const set = <K extends keyof ProblemDraft>(k: K, v: ProblemDraft[K]) =>
    setDraft((d) => ({ ...d, [k]: v }));

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const saved = problem ? await saveProblem(problem.id, draft) : await createProblem(draft);
      toast(problem ? "Problem updated." : "Problem created.", "success");
      onSaved(saved);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const valid = draft.title.trim().length > 0 && draft.description.trim().length > 0;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-ink/20" role="dialog" aria-modal="true" aria-label="Problem editor">
      <div className="flex h-full w-full max-w-4xl flex-col border-l border-border bg-background">
        <header className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
          <div>
            <p className="eyebrow">{problem ? "Edit problem" : "New problem"}</p>
            <h2 className="mt-0.5 text-heading-md text-ink">{problem ? problem.title : "Untitled"}</h2>
          </div>
          <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close editor">
            <X className="h-4 w-4" />
          </Button>
        </header>

        <Tabs value={tab} onValueChange={setTab} className="flex min-h-0 flex-1 flex-col">
          <TabsList className="mx-5 mt-3 self-start">
            <TabsTrigger value="details">Details</TabsTrigger>
            <TabsTrigger value="cases" disabled={!problem}>
              Test cases
            </TabsTrigger>
            <TabsTrigger value="grading" disabled={!problem}>
              Grading
            </TabsTrigger>
            <TabsTrigger value="reference" disabled={!problem}>
              Reference &amp; console
            </TabsTrigger>
          </TabsList>

        <TabsContent value="details" className="mt-0 min-h-0 flex-1 overflow-y-auto data-[state=inactive]:hidden">
        <div className="space-y-5 px-5 py-5">
          {!problem && (
            <p className="text-body-sm text-mute">
              Test cases, grading rules and the reference solution become available once the problem is created.
            </p>
          )}
          <div className="space-y-2">
            <label htmlFor="admin-title" className="eyebrow block text-mute">
              Title
            </label>
            <Input
              id="admin-title"
              value={draft.title}
              onChange={(e) => set("title", e.target.value)}
              placeholder="Two Sum"
              className="rounded-sm bg-card text-body-md text-ink"
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="admin-description" className="eyebrow block text-mute">
              Description (Markdown)
            </label>
            <Textarea
              id="admin-description"
              value={draft.description}
              onChange={(e) => set("description", e.target.value)}
              rows={14}
              placeholder={"Given an array of integers...\n\n### Input\n...\n### Output\n..."}
              className="rounded-sm bg-card font-mono text-code text-ink"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <label htmlFor="admin-difficulty" className="eyebrow block text-mute">
                Difficulty
              </label>
              <select
                id="admin-difficulty"
                value={draft.difficulty}
                onChange={(e) => set("difficulty", e.target.value as Difficulty)}
                className="h-9 w-full rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
              >
                {DIFFICULTIES.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <label htmlFor="admin-time" className="eyebrow block text-mute">
                Time limit (ms)
              </label>
              <Input
                id="admin-time"
                type="number"
                min={100}
                value={draft.timeLimitMs}
                onChange={(e) => set("timeLimitMs", Number(e.target.value))}
                className="rounded-sm bg-card text-body-md text-ink"
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="admin-memory" className="eyebrow block text-mute">
                Memory limit (MB)
              </label>
              <Input
                id="admin-memory"
                type="number"
                min={16}
                value={draft.memoryLimitMb}
                onChange={(e) => set("memoryLimitMb", Number(e.target.value))}
                className="rounded-sm bg-card text-body-md text-ink"
              />
            </div>
          </div>

          <TagEditor value={draft.tags} onChange={(v) => set("tags", v)} />

          <div className="space-y-2">
            <label htmlFor="admin-type" className="eyebrow block text-mute">
              Problem Type
            </label>
            <select
              id="admin-type"
              value={draft.type ?? "ALGORITHMIC"}
              onChange={(e) => set("type", e.target.value as ProblemType)}
              className="h-9 w-full rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
            >
              <option value="ALGORITHMIC">Algorithmic (Single File)</option>
              <option value="OOP_GRINDING">OOP Grinding (Multi-File Java)</option>
            </select>
          </div>

          {draft.type === "OOP_GRINDING" && (
            <StarterTemplateEditor
              value={draft.starterTemplates}
              onChange={(v) => set("starterTemplates", v)}
            />
          )}

          {error && (
            <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
              {error}
            </p>
          )}
        </div>
        </TabsContent>

        {problem && (
          <>
            <TabsContent value="cases" forceMount className="mt-0 min-h-0 flex-1 overflow-y-auto px-5 py-5 data-[state=inactive]:hidden">
              <TestCasesPanel
                problemId={problem.id}
                frozen={problem.status === "PUBLISHED"}
                hasReference={(authoring?.referenceSolution.length ?? 0) > 0}
              />
            </TabsContent>
            {(["grading", "reference"] as const).map((value) => (
              <TabsContent
                key={value}
                value={value}
                forceMount
                className="mt-0 min-h-0 flex-1 overflow-y-auto px-5 py-5 data-[state=inactive]:hidden"
              >
                {authoringError ? (
                  <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
                    {authoringError}
                  </p>
                ) : !authoring ? (
                  <p className="flex items-center gap-2 text-body-sm text-mute">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </p>
                ) : value === "grading" ? (
                  <>
                    {draft.type !== "OOP_GRINDING" && (
                      <p className="mb-4 rounded-sm border border-border bg-card px-3 py-2 text-body-sm text-mute">
                        Grading rules only apply to <strong className="text-ink">OOP Grinding</strong> problems. Algorithmic
                        problems are graded on test cases alone.
                      </p>
                    )}
                    <GradingPanel problemId={problem.id} authoring={authoring} onSaved={setAuthoring} />
                  </>
                ) : (
                  <ReferencePanel
                    problemId={problem.id}
                    authoring={authoring}
                    starterTemplates={draft.starterTemplates}
                    onSaved={setAuthoring}
                  />
                )}
              </TabsContent>
            ))}
          </>
        )}
        </Tabs>

        {tab === "details" && (
        <footer className="flex items-center justify-between gap-3 border-t border-border px-5 py-3">
          <p className="text-body-sm text-mute">
            {problem ? "Changes apply to metadata only." : "New problems start as Draft."}
          </p>
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={save} disabled={!valid || busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              {problem ? "Save" : "Create"}
            </Button>
          </div>
        </footer>
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Admin problems view
 * ------------------------------------------------------------------ */

export function AdminProblemsView() {
  const router = useRouter();
  const [res, setRes] = useState<Page<ProblemFull> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [type, setType] = useState<ProblemType | "">("");
  const [editing, setEditing] = useState<ProblemFull | null>(null);
  const [creating, setCreating] = useState(false);
  const [pending, setPending] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRes(await fetchAdminProblems({ page, q, status, difficulty, tags: [], type: type || undefined }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [page, q, status, difficulty, type]);

  useEffect(() => {
    load();
  }, [load]);

  const move = async (p: ProblemFull, target: ProblemLifecycle) => {
    setPending(p.id);
    try {
      const updated = await transitionProblem(p.id, target);
      setRes((r) => (r ? { ...r, content: r.content.map((x) => (x.id === updated.id ? updated : x)) } : r));
      toast(`Problem moved to ${target.toLowerCase()}.`, "success");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setPending(null);
    }
  };

  const remove = async (p: ProblemFull) => {
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
  const counts = useMemo(() => {
    const map = new Map<ProblemLifecycle, number>();
    for (const p of rows) {
      const s = (p.status ?? "DRAFT") as ProblemLifecycle;
      map.set(s, (map.get(s) ?? 0) + 1);
    }
    return map;
  }, [rows]);

  /**
   * Sau khi tạo bài mới, bài đó ở trạng thái DRAFT và nằm cuối danh sách.
   * Lọc theo DRAFT để người dùng thấy ngay bài vừa tạo.
   */
  const revealDrafts = () => {
    setStatus("DRAFT");
    setPage(1);
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow">Admin</p>
          <h1 className="mt-1 text-heading-lg text-ink">Problems</h1>
          <p className="mt-1 text-body-md text-mute">
            Create, edit and move problems through their lifecycle.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus className="h-4 w-4" />
          New problem
        </Button>
      </header>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1 basis-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") setPage(1);
            }}
            placeholder="Search problems"
            aria-label="Search problems"
            className="h-10 rounded-sm bg-card pl-9 text-body-md text-ink"
          />
        </div>
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(1);
          }}
          aria-label="Filter by lifecycle status"
          className="h-10 rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
        >
          <option value="">Any status</option>
          {LIFECYCLES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        <select
          value={difficulty}
          onChange={(e) => {
            setDifficulty(e.target.value);
            setPage(1);
          }}
          aria-label="Filter by difficulty"
          className="h-10 rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
        >
          <option value="">Any difficulty</option>
          {DIFFICULTIES.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
        <select
          value={type}
          onChange={(e) => {
            setType(e.target.value as ProblemType | "");
            setPage(1);
          }}
          aria-label="Filter by problem type"
          className="h-10 rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
        >
          <option value="">All Types (DSA & OOP)</option>
          <option value="ALGORITHMIC">DSA (Thuật toán)</option>
          <option value="OOP_GRINDING">OOP (Hướng đối tượng)</option>
        </select>
      </div>

      {/* Status tally — counts describe the rows on screen, so they are labelled */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body-sm text-mute">On this page:</span>
        {LIFECYCLES.map((s) => (
          <button
            key={s.value}
            onClick={() => {
              setStatus((cur) => (cur === s.value ? "" : s.value));
              setPage(1);
            }}
            aria-pressed={status === s.value}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1 text-body-sm transition-colors hover:bg-hairline-soft",
              status === s.value && "border-ink bg-ink text-white hover:bg-ink/90"
            )}
          >
            {s.label}
            <span className="font-mono text-[11px] opacity-70">{counts.get(s.value) ?? 0}</span>
          </button>
        ))}
      </div>

      {error && (
        <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
          {error}
        </p>
      )}

      {/* Table */}
      <section aria-label="Problem administration" className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="hidden items-center gap-3 border-b border-border px-5 py-3 text-[11px] font-medium uppercase tracking-wider text-mute md:flex">
          <span className="w-14">ID</span>
          <span className="min-w-0 flex-1">Title</span>
          <span className="w-24">Status</span>
          <span className="w-24">Difficulty</span>
          <span className="w-16">Tests</span>
          <span className="w-44 text-right">Actions</span>
        </div>

        {loading && !res && (
          <div className="space-y-2 p-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-9 animate-pulse rounded bg-hairline-soft" />
            ))}
          </div>
        )}

        {rows.map((p) => {
          const s = (p.status ?? "DRAFT") as ProblemLifecycle;
          const targets = EDGES[s];
          return (
            <div
              key={p.id}
              className="grid grid-cols-1 items-center gap-2 border-t border-border px-5 py-3.5 md:grid-cols-[56px_minmax(0,1fr)_96px_96px_64px_176px] md:gap-3"
            >
              <span className="font-mono text-body-sm text-mute">{p.id}</span>

              <span className="min-w-0">
                <button
                  onClick={() => setEditing(p)}
                  className="block max-w-full truncate text-left text-body-md text-ink transition-colors hover:text-link"
                >
                  {p.title}
                  {p.type === "OOP_GRINDING" ? (
                    <span className="ml-2 inline-flex items-center rounded border border-purple-500/25 bg-purple-500/10 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-purple-600 dark:text-purple-400">
                      OOP
                    </span>
                  ) : (
                    <span className="ml-2 inline-flex items-center rounded border border-cyan-500/25 bg-cyan-500/10 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-cyan-600 dark:text-cyan-400">
                      DSA
                    </span>
                  )}
                </button>
                <span className="mt-1 flex flex-wrap gap-1">
                  {p.tags.slice(0, 4).map((t) => (
                    <span key={t} className="rounded-full bg-hairline-soft px-2 py-px font-mono text-[10px] text-mute">
                      {t}
                    </span>
                  ))}
                </span>
              </span>

              <span>
                <Badge variant={STATUS_TONE[s]} className="font-mono text-[11px]">
                  {s}
                </Badge>
              </span>

              <span>
                <DifficultyBadge value={p.difficulty} />
              </span>

              <span className="font-mono text-body-sm text-mute">{p.testCaseCount ?? 0}</span>

              <span className="flex items-center justify-start gap-1 md:justify-end">
                {targets.map((t) => (
                  <Button
                    key={t}
                    size="sm"
                    variant="outline"
                    disabled={pending === p.id}
                    onClick={() => move(p, t)}
                    className="h-7 px-2 text-body-sm"
                    title={`Move to ${t.toLowerCase()}`}
                  >
                    {pending === p.id ? (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    ) : t === "ARCHIVED" ? (
                      <Archive className="h-3 w-3" />
                    ) : t === "PUBLISHED" ? (
                      <Check className="h-3 w-3" />
                    ) : (
                      <ChevronRight className="h-3 w-3" />
                    )}
                    {t === "PUBLISHED" ? "Publish" : t === "ARCHIVED" ? "Archive" : t.charAt(0) + t.slice(1).toLowerCase()}
                  </Button>
                ))}
                <Button
                  size="icon-sm"
                  variant="ghost"
                  disabled={pending === p.id}
                  onClick={() => remove(p)}
                  aria-label={`Delete ${p.title}`}
                  title="Delete"
                  className="text-mute hover:text-destructive"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  onClick={() => router.push(`/problems/${p.slug}`)}
                  aria-label={`View ${p.title}`}
                  title="View"
                >
                  <FileText className="h-3.5 w-3.5" />
                </Button>
              </span>
            </div>
          );
        })}

        {res && rows.length === 0 && (
          <div className="border-t border-border p-12 text-center">
            <p className="font-medium text-ink">No problems match these filters</p>
            <p className="mx-auto mt-1 max-w-[40ch] text-body-md text-mute">
              Adjust the search or clear the filters to see everything.
            </p>
            <Button
              variant="outline"
              size="sm"
              className="mt-5"
              onClick={() => {
                setQ("");
                setStatus("");
                setDifficulty("");
                setPage(1);
              }}
            >
              Clear filters
            </Button>
          </div>
        )}

        {res && rows.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-hairline-soft px-5 py-3">
            <span className="font-mono text-[11px] text-mute">
              Showing {res.totalElements === 0 ? 0 : res.number * res.size + 1}–
              {res.number * res.size + rows.length} of {res.totalElements}
            </span>
            <Pagination page={page} totalPages={res.totalPages} onChange={setPage} />
          </div>
        )}
      </section>

      {creating && (
        <Editor
          problem={null}
          onClose={() => setCreating(false)}
          onSaved={(created) => {
            setCreating(false);
            revealDrafts();
            // Mở tiếp bài vừa tạo để thêm testcase / rule chấm ngay.
            setEditing(created);
          }}
        />
      )}
      {editing && (
        <Editor
          problem={editing}
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
