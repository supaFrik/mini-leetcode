"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FILE_NAME, LANGUAGES, TEMPLATES } from "@/lib/templates";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import { fetchAuthoring, fetchProblem, saveProblem, unlockProblem, type ProblemDraft } from "@/lib/services";
import type { Difficulty, ProblemAuthoring, ProblemFull, ProblemType, SubmissionFile } from "@/types/api";
import { GradingPanel, ReferencePanel, SourceFilesEditor, TestCasesPanel, parseSourceFiles } from "./problem-authoring";
import type { SaveState } from "./save-state";

/*
 * Soạn một đề trên cả trang: đề bài tự lưu 1 s sau lần sửa cuối, các tab Tests / Grading / Reference
 * dùng chung với Course Builder (specs/course-builder.spec.md).
 */

const field = "space-y-1 text-body-sm text-mute";
const select = "block h-9 w-full rounded-sm border border-border bg-card px-2 text-body-md text-ink";
const DEFAULT_STARTER: SubmissionFile[] = [{ path: "Main.java", content: "public class Main {\n    public static void main(String[] args) {\n    }\n}\n" }];

function toDraft(p: ProblemFull): ProblemDraft {
  return {
    title: p.title, description: p.description, difficulty: p.difficulty, timeLimitMs: p.timeLimitMs,
    memoryLimitMb: p.memoryLimitMb, tags: [...p.tags], type: p.type ?? "ALGORITHMIC", starterTemplates: p.starterTemplates ?? null,
  };
}

/** Starter template lưu dạng chuỗi JSON; trống hoặc hỏng thì hiện khung mặc định. */
function starterFiles(value: string | null | undefined): SubmissionFile[] {
  const files = parseSourceFiles(value);
  return files.length > 0 ? files : DEFAULT_STARTER;
}

/** Code khởi đầu bài DSA: một ô cho mỗi ngôn ngữ; để trống = dùng mẫu mặc định (placeholder). */
function DsaStarterField({ value, onChange, disabled }: { value: string | null | undefined; onChange: (v: string) => void; disabled?: boolean }) {
  const files = parseSourceFiles(value);
  const get = (path: string) => files.find((f) => f.path === path)?.content ?? "";
  const update = (path: string, content: string) => {
    const next = LANGUAGES.map((l) => FILE_NAME[l.value])
      .map((p) => ({ path: p, content: p === path ? content : get(p) }))
      .filter((f) => f.content.trim());
    onChange(next.length ? JSON.stringify(next) : "");
  };
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {LANGUAGES.map((l) => (
        <label key={l.value} className={field}>
          Starter code — {l.label} ({FILE_NAME[l.value]}), empty = default
          <Textarea rows={10} disabled={disabled} value={get(FILE_NAME[l.value])} placeholder={TEMPLATES[l.value]}
            onChange={(e) => update(FILE_NAME[l.value], e.target.value)} className="font-mono text-xs" />
        </label>
      ))}
    </div>
  );
}

/** Cùng luật chuẩn hoá tag với server: bỏ trống, gộp trùng không phân biệt hoa/thường. */
function normalizeTags(raw: string): string[] {
  const seen = new Map<string, string>();
  for (const part of raw.split(",")) {
    const t = part.trim();
    if (t && !seen.has(t.toLowerCase())) seen.set(t.toLowerCase(), t);
  }
  return Array.from(seen.values());
}

function TagsField({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState(value.join(", "));
  const joined = value.join(", ");
  useEffect(() => {
    // Chỉ đồng bộ từ ngoài vào khi khác nội dung đang gõ (giữ nguyên dấu phẩy cuối khi đang nhập).
    if (normalizeTags(text).join(", ") !== joined) setText(joined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [joined]);
  return (
    <label className={`block ${field}`}>Tags
      <Input value={text} placeholder="Array, Hash Table" onChange={(e) => { setText(e.target.value); onChange(normalizeTags(e.target.value)); }} />
    </label>
  );
}

function Locked({ learners, onUnlock, busy }: { learners: number; onUnlock: () => void; busy: boolean }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-sm border border-border bg-hairline-soft px-3 py-3 text-body-sm">
      <span className="flex items-center gap-2 text-ink"><Lock className="h-4 w-4" />Locked while live
        <span className="text-mute">{learners > 0 ? `· ${learners} learner${learners === 1 ? "" : "s"} on this problem` : ""}</span></span>
      <Button size="sm" variant="outline" disabled={busy} onClick={onUnlock}>Unlock to edit</Button>
    </div>
  );
}

export function ProblemEditor({ problemId, label, canEdit, learners = 0, current, onLoad, onProblem, onSaveState, children }: {
  problemId: number;
  /** Tên hiển thị trong hộp xác nhận mở khoá, vd "Stage 2". */
  label: string;
  canEdit: boolean;
  learners?: number;
  /** Bản mới nhất từ bên ngoài (vd sau khi đổi trạng thái vòng đời) để cập nhật khoá Tests / Grading. */
  current?: ProblemFull | null;
  onLoad?: (p: ProblemFull) => void;
  /** Đề vừa lưu hoặc vừa mở khoá. */
  onProblem: (p: ProblemFull) => void;
  onSaveState: (s: SaveState) => void;
  children?: React.ReactNode;
}) {
  const [problem, setProblem] = useState<ProblemFull | null>(null);
  const [draft, setDraft] = useState<ProblemDraft | null>(null);
  const [authoring, setAuthoring] = useState<ProblemAuthoring | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [unlocking, setUnlocking] = useState(false);
  const saved = useRef("");
  const ask = useConfirm();

  useEffect(() => {
    let off = false;
    setProblem(null);
    setDraft(null);
    setAuthoring(null);
    setError(null);
    Promise.all([fetchProblem(String(problemId)), fetchAuthoring(problemId)])
      .then(([p, a]) => {
        if (off) return;
        setProblem(p);
        setDraft(toDraft(p));
        saved.current = JSON.stringify(toDraft(p));
        setAuthoring(a);
        onLoad?.(p);
      })
      .catch((e) => !off && setError(errorMessage(e)));
    return () => { off = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [problemId]);

  useEffect(() => { if (current && current.id === problemId) setProblem(current); }, [current, problemId]);

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
    const who = learners > 0 ? `${learners} learner${learners === 1 ? "" : "s"} currently on it` : "learners";
    if (!(await ask({ title: `Unlock ${label}?`, description: `It will be unavailable to ${who} until you publish again.`, action: "Unlock" }))) return;
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
  const locked = frozen && canEdit && <Locked learners={learners} busy={unlocking} onUnlock={unlock} />;
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
            <div className={`block ${field}`}>Statement
              <RichTextEditor value={draft.description} onChange={(v) => set("description", v)} label="Statement" disabled={!canEdit} className="mt-1" />
            </div>
            <div className="grid gap-3 sm:grid-cols-4">
              <label className={field}>Difficulty
                <select className={select} value={draft.difficulty} onChange={(e) => set("difficulty", e.target.value as Difficulty)}>
                  {(["EASY", "MEDIUM", "HARD"] as const).map((d) => <option key={d}>{d}</option>)}
                </select>
              </label>
              <label className={field}>Type
                <select className={select} value={draft.type ?? "ALGORITHMIC"} onChange={(e) => set("type", e.target.value as ProblemType)}>
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
            <TagsField value={draft.tags} onChange={(tags) => set("tags", tags)} />
            {draft.type !== "OOP_GRINDING" && (
              <DsaStarterField value={draft.starterTemplates} onChange={(v) => set("starterTemplates", v)} disabled={!canEdit} />
            )}
            {draft.type === "OOP_GRINDING" && (
              <SourceFilesEditor label="Starter files (.java)" files={starterFiles(draft.starterTemplates)}
                onChange={(files) => set("starterTemplates", JSON.stringify(files))} />
            )}
          </fieldset>
        </TabsContent>
        <TabsContent value="tests" forceMount className="pt-4 data-[state=inactive]:hidden">
          {locked && <div className="mb-4">{locked}</div>}
          <TestCasesPanel problemId={problem.id} frozen={frozen || !canEdit} hasReference={(authoring?.referenceSolution.length ?? 0) > 0} />
        </TabsContent>
        <TabsContent value="grading" className="pt-4">
          {frozen ? locked : authoring && <GradingPanel problemId={problem.id} authoring={authoring} onSaved={setAuthoring} />}
        </TabsContent>
        <TabsContent value="reference" className="pt-4">
          {authoring && <ReferencePanel problemId={problem.id} authoring={authoring} starterTemplates={draft.starterTemplates} onSaved={setAuthoring} />}
        </TabsContent>
      </Tabs>
      {error && <p role="alert" className="rounded-sm border border-destructive/30 bg-destructive/5 px-3 py-2 text-body-sm text-destructive">{error}</p>}
      {children}
    </div>
  );
}
