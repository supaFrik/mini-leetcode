"use client";
import { useEffect, useRef, useState } from "react";
import { Loader2, Plus, Search, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { fetchTopics, saveTopic } from "@/lib/services";
import type { ProblemFull, TopicSummary } from "@/types/api";
import { ProblemEditor } from "../problem-editor";
import { slugify } from "../admin-study-plans";
import type { SaveState } from "../admin-topics";
import type { StageNode, TopicNode } from "./model";

/* Cột phải khi chọn một stage: soạn đề (tự lưu) + các chủ đề trước stage (specs/course-builder.spec.md). */

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
  const [status, setStatus] = useState(stage.status);
  return (
    <ProblemEditor problemId={stage.problemId} label={label} canEdit={canEdit} learners={stage.learners}
      onLoad={(p) => setStatus(p.status ?? null)} onProblem={(p) => { setStatus(p.status ?? null); onProblem(p); }} onSaveState={onSaveState}>
      <StageTopics topics={stage.topics} onChange={onTopics} onOpen={onOpenTopic} focusSignal={focusTopics} />
      {live && status !== "PUBLISHED" && <p className="text-body-sm text-mute">This stage is unlocked: learners cannot submit it until you publish changes.</p>}
    </ProblemEditor>
  );
}
