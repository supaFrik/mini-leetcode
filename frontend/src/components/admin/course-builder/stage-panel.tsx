"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, Lock, Plus, Search, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { errorMessage } from "@/lib/api";
import {
  fetchAuthoring, fetchProblem, fetchTopics, saveProblem, saveTopic, unlockProblem, type ProblemDraft,
} from "@/lib/services";
import type { Difficulty, ProblemAuthoring, ProblemFull, ProblemType, SubmissionFile, TopicSummary } from "@/types/api";
import { GradingPanel, ReferencePanel, SourceFilesEditor, TestCasesPanel } from "../problem-authoring";
import { slugify } from "../admin-study-plans";
import type { SaveState } from "../admin-topics";
import type { StageNode, TopicNode } from "./model";

/* Cột phải khi chọn một stage: soạn đề (tự lưu) + các chủ đề trước stage (specs/course-builder.spec.md). */

const field = "space-y-1 text-body-sm text-mute";
const DEFAULT_STARTER: SubmissionFile[] = [{ path: "Main.java", content: "public class Main {\n    public static void main(String[] args) {\n    }\n}\n" }];

function toDraft(p: ProblemFull): ProblemDraft {
  return {
    title: p.title, description: p.description, difficulty: p.difficulty, timeLimitMs: p.timeLimitMs,
    memoryLimitMb: p.memoryLimitMb, tags: [...p.tags], type: p.type ?? "ALGORITHMIC", starterTemplates: p.starterTemplates ?? null,
  };
}

function starterFiles(value: string | null | undefined): SubmissionFile[] {
  try {
    const parsed = value ? JSON.parse(value) : null;
    if (Array.isArray(parsed) && parsed.length > 0) return parsed;
  } catch {
    /* bỏ qua JSON hỏng: hiện khung mặc định */
  }
  return DEFAULT_STARTER;
}

function Locked({ learners, onUnlock, busy }: { learners: number; onUnlock: () => void; busy: boolean }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-sm border border-border bg-hairline-soft px-3 py-3 text-body-sm">
      <span className="flex items-center gap-2 text-ink"><Lock className="h-4 w-4" />Locked while live
        <span className="text-mute">{learners > 0 ? `· ${learners} learner${learners === 1 ? "" : "s"} on this stage` : ""}</span></span>
      <Button size="sm" variant="outline" disabled={busy} onClick={onUnlock}>Unlock to edit</Button>
    </div>
  );
}

/** Gắn / tạo / gỡ chủ đề của stage. */
function StageTopics({ topics, onChange, onOpen, focusSignal }: {
  topics: TopicNode[]; onChange: (t: TopicNode[]) => void; onOpen: (t: TopicNode) => void; focusSignal: number;
}) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<TopicSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { if (focusSignal > 0) input.current?.focus(); }, [focusSignal]);
  useEffect(() => {
    if (!q.trim()) { setResults([]); return; }
    let off = false;
    const t = setTimeout(() => { fetchTopics(q.trim()).then((r) => !off && setResults(r)).catch(() => !off && setResults([])); }, 250);
    return () => { off = true; clearTimeout(t); };
  }, [q]);

  const create = async () => {
    const title = q.trim();
    setBusy(true);
    setError(null);
    try {
      const t = await saveTopic(null, {
        slug: `${slugify(title) || "topic"}-${Date.now().toString(36).slice(-4)}`, title, estMinutes: 10,
        steps: [{ type: "THEORY", title: "Theory", content: { markdown: `## ${title}\n\n` } }],
      });
      const node = { id: t.id, slug: t.slug, title: t.title, status: t.status };
      onChange([...topics, node]);
      setQ("");
      onOpen(node);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const visible = results.filter((r) => !topics.some((t) => t.id === r.id)).slice(0, 6);
  return (
    <section className="space-y-2" aria-label="Topics before this stage">
      <h3 className="eyebrow text-mute">Topics before this stage</h3>
      {topics.length === 0 && <p className="text-body-sm text-mute">No topics yet.</p>}
      <ul className="divide-y divide-border rounded-sm border border-border">
        {topics.map((t) => (
          <li key={t.id} className="flex items-center gap-2 px-3 py-2 text-body-sm">
            <span className="min-w-0 flex-1 truncate text-ink">{t.title}</span>
            <Badge variant={t.status === "PUBLISHED" ? "success" : "secondary"}>{t.status ?? "PUBLISHED"}</Badge>
            <Button size="sm" variant="ghost" onClick={() => onOpen(t)}>Open</Button>
            <Button size="icon-sm" variant="ghost" aria-label={`Detach ${t.title}`} onClick={() => onChange(topics.filter((x) => x.id !== t.id))}>
              <X className="h-4 w-4" /></Button>
          </li>
        ))}
      </ul>
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" aria-hidden />
        <Input ref={input} value={q} onChange={(e) => setQ(e.target.value)} className="pl-8" aria-label="Find or create a topic"
          placeholder="Find a topic, or type a title to create one" />
      </div>
      {q.trim() && (
        <ul className="rounded-sm border border-border bg-card text-body-sm" aria-label="Topic results">
          {visible.map((r) => (
            <li key={r.id}>
              <button type="button" className="flex w-full justify-between gap-3 px-3 py-1.5 text-left hover:bg-hairline-soft"
                onClick={() => { onChange([...topics, { id: r.id, slug: r.slug, title: r.title, status: r.status }]); setQ(""); }}>
                <span>{r.title}</span><span className="text-mute">{r.stepCount} steps · {r.status}</span>
              </button>
            </li>
          ))}
          <li>
            <button type="button" disabled={busy} onClick={create}
              className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-link hover:bg-hairline-soft">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}Create topic “{q.trim()}”
            </button>
          </li>
        </ul>
      )}
      {error && <p role="alert" className="text-body-sm text-destructive">{error}</p>}
    </section>
  );
}

export function StagePanel({ stage, label, live, canEdit, onProblem, onTopics, onOpenTopic, onSaveState, focusTopics }: {
  stage: StageNode; label: string; live: boolean; canEdit: boolean;
  /** Đề đã lưu / đổi trạng thái: cập nhật tiêu đề, trạng thái trên outline. */
  onProblem: (p: ProblemFull) => void;
  onTopics: (t: TopicNode[]) => void;
  onOpenTopic: (t: TopicNode) => void;
  onSaveState: (s: SaveState) => void;
  focusTopics: number;
}) {
  const [problem, setProblem] = useState<ProblemFull | null>(null);
  const [draft, setDraft] = useState<ProblemDraft | null>(null);
  const [authoring, setAuthoring] = useState<ProblemAuthoring | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [unlocking, setUnlocking] = useState(false);
  const saved = useRef("");

  useEffect(() => {
    let off = false;
    setProblem(null);
    setDraft(null);
    setAuthoring(null);
    setError(null);
    Promise.all([fetchProblem(String(stage.problemId)), fetchAuthoring(stage.problemId)])
      .then(([p, a]) => {
        if (off) return;
        setProblem(p);
        setDraft(toDraft(p));
        saved.current = JSON.stringify(toDraft(p));
        setAuthoring(a);
      })
      .catch((e) => !off && setError(errorMessage(e)));
    return () => { off = true; };
  }, [stage.problemId]);

  // Autosave đề bài 1 s sau lần sửa cuối.
  const json = useMemo(() => (draft ? JSON.stringify(draft) : ""), [draft]);
  useEffect(() => {
    if (!draft || !problem || !canEdit || json === saved.current) return;
    if (!draft.title.trim() || !draft.description.trim()) { onSaveState("invalid"); return; }
    const t = setTimeout(async () => {
      onSaveState("saving");
      try {
        const p = await saveProblem(problem.id, draft);
        saved.current = json;
        setProblem(p);
        onProblem(p);
        onSaveState("saved");
      } catch (e) {
        setError(errorMessage(e));
        onSaveState("error");
      }
    }, 1000);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [json]);

  const unlock = async () => {
    if (!problem) return;
    const n = stage.learners;
    const who = n > 0 ? `${n} learner${n === 1 ? "" : "s"} currently on it` : "learners";
    if (!window.confirm(`${label} will be unavailable to ${who} until you publish again. Unlock to edit?`)) return;
    setUnlocking(true);
    try {
      const p = await unlockProblem(problem.id);
      setProblem(p);
      onProblem(p);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setUnlocking(false);
    }
  };

  if (error && !problem) return <p role="alert" className="text-body-md text-destructive">{error}</p>;
  if (!problem || !draft) return <p className="flex items-center gap-2 text-body-sm text-mute"><Loader2 className="h-4 w-4 animate-spin" />Loading…</p>;

  const set = <K extends keyof ProblemDraft>(k: K, v: ProblemDraft[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d));
  const frozen = problem.status === "PUBLISHED";
  return (
    <div className="space-y-6">
      <Tabs defaultValue="statement">
        <TabsList>
          <TabsTrigger value="statement">Statement</TabsTrigger>
          <TabsTrigger value="tests">Tests</TabsTrigger>
          <TabsTrigger value="grading">Grading</TabsTrigger>
          <TabsTrigger value="reference">Reference</TabsTrigger>
        </TabsList>
        <TabsContent value="statement" className="space-y-4 pt-4">
          <fieldset disabled={!canEdit} className="space-y-4">
            <label className={`block ${field}`}>Title
              <Input value={draft.title} onChange={(e) => set("title", e.target.value)} />
            </label>
            <label className={`block ${field}`}>Statement (Markdown)
              <Textarea rows={14} className="font-mono" value={draft.description} onChange={(e) => set("description", e.target.value)} />
            </label>
            <div className="grid gap-3 sm:grid-cols-4">
              <label className={field}>Difficulty
                <select className="block h-9 w-full rounded-sm border border-border bg-card px-2 text-body-md text-ink" value={draft.difficulty}
                  onChange={(e) => set("difficulty", e.target.value as Difficulty)}>
                  {(["EASY", "MEDIUM", "HARD"] as const).map((d) => <option key={d}>{d}</option>)}
                </select>
              </label>
              <label className={field}>Type
                <select className="block h-9 w-full rounded-sm border border-border bg-card px-2 text-body-md text-ink" value={draft.type ?? "ALGORITHMIC"}
                  onChange={(e) => set("type", e.target.value as ProblemType)}>
                  <option value="ALGORITHMIC">Algorithmic</option>
                  <option value="OOP_GRINDING">OOP (multi-file)</option>
                </select>
              </label>
              <label className={field}>Time (ms)
                <Input type="number" min={100} value={draft.timeLimitMs} onChange={(e) => set("timeLimitMs", Number(e.target.value))} />
              </label>
              <label className={field}>Memory (MB)
                <Input type="number" min={16} value={draft.memoryLimitMb} onChange={(e) => set("memoryLimitMb", Number(e.target.value))} />
              </label>
            </div>
            {draft.type === "OOP_GRINDING" && (
              <SourceFilesEditor label="Starter files (.java)" files={starterFiles(draft.starterTemplates)}
                onChange={(files) => set("starterTemplates", JSON.stringify(files))} />
            )}
          </fieldset>
        </TabsContent>
        <TabsContent value="tests" forceMount className="pt-4 data-[state=inactive]:hidden">
          {frozen && canEdit && <div className="mb-4"><Locked learners={stage.learners} busy={unlocking} onUnlock={unlock} /></div>}
          <TestCasesPanel problemId={problem.id} frozen={frozen || !canEdit} hasReference={(authoring?.referenceSolution.length ?? 0) > 0} />
        </TabsContent>
        <TabsContent value="grading" className="pt-4">
          {frozen ? (canEdit && <Locked learners={stage.learners} busy={unlocking} onUnlock={unlock} />)
            : authoring && <GradingPanel problemId={problem.id} authoring={authoring} onSaved={setAuthoring} />}
        </TabsContent>
        <TabsContent value="reference" className="pt-4">
          {authoring && <ReferencePanel problemId={problem.id} authoring={authoring} starterTemplates={draft.starterTemplates} onSaved={setAuthoring} />}
        </TabsContent>
      </Tabs>
      {error && <p role="alert" className="rounded-sm border border-destructive/30 bg-destructive/5 px-3 py-2 text-body-sm text-destructive">{error}</p>}
      <StageTopics topics={stage.topics} onChange={onTopics} onOpen={onOpenTopic} focusSignal={focusTopics} />
      {live && !frozen && <p className="text-body-sm text-mute">This stage is unlocked: learners cannot submit it until you publish changes.</p>}
    </div>
  );
}
