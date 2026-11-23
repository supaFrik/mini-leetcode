"use client";
import { useEffect, useState } from "react";
import { BookOpen, Code2, FolderKanban, Loader2, Plus, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { createProblem, fetchAdminProblems, fetchTopics, saveTopic } from "@/lib/services";
import type { ProblemFull, ProblemType, TopicSummary } from "@/types/api";
import { ProblemEditor } from "../problem-editor";
import { slugify } from "@/lib/slug";
import type { SaveState } from "../save-state";
import type { AddMode, StageNode, TopicNode } from "./model";

/* Cột phải của Course Builder: soạn đề (tự lưu) và form thêm lesson / đề vào session. */

export function StagePanel({ stage, label, live, canEdit, onProblem, onSaveState }: {
  stage: StageNode; label: string; live: boolean; canEdit: boolean;
  /** Đề đã lưu / đổi trạng thái: cập nhật tiêu đề, trạng thái trên outline. */
  onProblem: (p: ProblemFull) => void;
  onSaveState: (s: SaveState) => void;
}) {
  const [status, setStatus] = useState(stage.status);
  return (
    <ProblemEditor problemId={stage.problemId} label={label} canEdit={canEdit} learners={stage.learners}
      onLoad={(p) => setStatus(p.status ?? null)} onProblem={(p) => { setStatus(p.status ?? null); onProblem(p); }} onSaveState={onSaveState}>
      {live && status !== "PUBLISHED" && <p className="text-body-sm text-mute">This problem is unlocked: learners cannot submit it until you publish changes.</p>}
    </ProblemEditor>
  );
}

const TITLES: Record<AddMode, string> = { lesson: "Add lesson", problem: "Add problem" };
const TYPES: { label: string; value?: ProblemType }[] = [
  { label: "All" }, { label: "DSA", value: "ALGORITHMIC" }, { label: "OOP", value: "OOP_GRINDING" },
];

/** Thêm mục vào session: tìm lesson / đề có sẵn, hoặc tạo mới ở cuối danh sách. Đề OOP được thêm dạng project stage. */
export function AddPanel({ mode, session, exclude, onTopic, onProblem }: {
  mode: AddMode; session: string; exclude: Set<number>;
  onTopic: (t: TopicNode) => void; onProblem: (p: ProblemFull) => void;
}) {
  const [q, setQ] = useState("");
  const [type, setType] = useState<ProblemType | undefined>();
  const [lessons, setLessons] = useState<TopicSummary[]>([]);
  const [problems, setProblems] = useState<ProblemFull[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let off = false;
    const t = setTimeout(() => {
      if (mode === "lesson") fetchTopics(q.trim()).then((r) => !off && setLessons(r)).catch(() => !off && setLessons([]));
      else fetchAdminProblems({ page: 1, q: q.trim(), difficulty: "", status: "", tags: [], type, scope: "ALL" })
        .then((r) => !off && setProblems(r.content)).catch(() => !off && setProblems([]));
    }, 250);
    return () => { off = true; clearTimeout(t); };
  }, [q, mode, type]);

  const run = async (job: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try { await job(); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  const createLesson = () => run(async () => {
    const title = q.trim() || "New lesson";
    const t = await saveTopic(null, {
      slug: `${slugify(title) || "lesson"}-${Date.now().toString(36).slice(-4)}`, title, estMinutes: 10,
      steps: [{ type: "THEORY", title: "Theory", content: { markdown: `## ${title}\n\n` } }],
    });
    onTopic({ id: t.id, slug: t.slug, title: t.title, status: t.status, stepCount: t.steps.length });
  });
  const createNewProblem = () => run(async () => onProblem(await createProblem({
    title: q.trim() || session, description: "Describe the task.", difficulty: "EASY", timeLimitMs: 1000, memoryLimitMb: 256,
    tags: [], type: type ?? "ALGORITHMIC", starterTemplates: null,
  })));

  const row = "flex w-full items-center gap-2 py-1.5 text-left hover:text-link";
  return (
    <section className="space-y-3" aria-label={TITLES[mode]}>
      <h2 className="text-body-md font-medium text-ink">{TITLES[mode]} · <span className="text-mute">{session}</span></h2>
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" aria-hidden />
        <Input autoFocus value={q} onChange={(e) => setQ(e.target.value)} className="pl-8"
          aria-label={mode === "lesson" ? "Search lessons" : "Search problems"}
          placeholder={mode === "lesson" ? "Search the lesson library" : "Search problems by title or slug"} />
      </div>
      {mode === "problem" && (
        <div className="flex gap-4 text-body-sm" role="group" aria-label="Problem type">
          {TYPES.map((t) => (
            <button key={t.label} type="button" aria-pressed={type === t.value} onClick={() => setType(t.value)}
              className={type === t.value ? "font-semibold text-ink" : "text-mute hover:text-ink"}>{t.label}</button>
          ))}
        </div>
      )}
      <ul className="text-body-sm" aria-label={mode === "lesson" ? "Lesson results" : "Problem results"}>
        {mode === "lesson" && lessons.slice(0, 10).map((r) => (
          <li key={r.id}>
            <button type="button" className={row} onClick={() => onTopic({ id: r.id, slug: r.slug, title: r.title, status: r.status, stepCount: r.stepCount })}>
              <BookOpen className="h-4 w-4 shrink-0 text-mute" aria-hidden />
              <span className="min-w-0 flex-1 truncate">{r.title}</span><span className="text-mute">{r.stepCount} steps</span>
            </button>
          </li>
        ))}
        {mode === "problem" && problems.filter((p) => !exclude.has(p.id)).slice(0, 10).map((p) => {
          const Icon = p.type === "OOP_GRINDING" ? FolderKanban : Code2;
          return (
            <li key={p.id}>
              <button type="button" className={row} onClick={() => onProblem(p)}>
                <Icon className="h-4 w-4 shrink-0 text-mute" aria-hidden />
                <span className="min-w-0 flex-1 truncate">{p.title}</span>
                <span className="text-mute">{p.type === "OOP_GRINDING" ? "OOP" : "DSA"} · {p.status ?? "DRAFT"}</span>
              </button>
            </li>
          );
        })}
        {(mode === "lesson" ? lessons.length : problems.length) === 0 && <li className="py-1.5 text-mute">No matches.</li>}
        <li>
          <button type="button" disabled={busy} className="flex items-center gap-1 py-1.5 text-mute hover:text-ink disabled:opacity-50"
            onClick={() => void (mode === "lesson" ? createLesson() : createNewProblem())}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
            {mode === "lesson" ? "Create new lesson" : "Create new problem"}
          </button>
        </li>
      </ul>
      {error && <p role="alert" className="text-body-sm text-destructive">{error}</p>}
    </section>
  );
}
