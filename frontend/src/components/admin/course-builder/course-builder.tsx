"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Archive, Eye, FileEdit, Loader2, Rocket, Settings, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useCanEdit } from "@/hooks/use-can-edit";
import { fetchProblem, fetchStudyPlan, publishStudyPlan, transitionStudyPlan, updateStudyPlan } from "@/lib/services";
import type { AuthoringTopic, ProblemFull, PublishIssue, StudyPlanDetail, StudyPlanStatus } from "@/types/api";
import { TopicEditor } from "../admin-topics";
import type { SaveState } from "../save-state";
import { PLAN_STATUS_VARIANT } from "../status";
import {
  findEntry, fromDetail, newKey, parseNode, signature, toDraft,
  type EntryNode, type NodeId, type PlanModel, type SessionNode, type StageNode, type TopicNode,
} from "./model";
import { Outline, entryLabel } from "./outline";
import { PublishDialog } from "./publish-dialog";
import { AddPanel, StagePanel } from "./stage-panel";
import { useConfirm } from "@/components/ui/confirm";

/* /admin/study-plans/[slug] — Course Builder: outline session / mục bên trái, form soạn mục đang chọn bên phải. */

const field = "space-y-1 text-body-sm text-mute";

/** Áp trạng thái đề / topic mới từ server lên model mà giữ key (lựa chọn trên outline không đổi). */
function mergeStatuses(model: PlanModel, detail: StudyPlanDetail): PlanModel {
  const fresh = fromDetail(detail).sessions.flatMap((s) => s.entries);
  const problems = new Map(fresh.flatMap((e) => (e.problem ? [[e.problem.problemId, e.problem] as const] : [])));
  const topics = new Map(fresh.flatMap((e) => (e.topic ? [[e.topic.id, e.topic] as const] : [])));
  return {
    ...model, status: detail.status, slug: detail.slug,
    sessions: model.sessions.map((s) => ({
      ...s,
      entries: s.entries.map((e) => {
        const p = e.problem && problems.get(e.problem.problemId);
        if (p) return { ...e, problem: { ...e.problem!, status: p.status, learners: p.learners, title: p.title ?? e.problem!.title } };
        const t = e.topic && topics.get(e.topic.id);
        return t ? { ...e, topic: { ...e.topic!, status: t.status } } : e;
      }),
    })),
  };
}

export function CourseBuilder({ slug }: { slug: string }) {
  const canEditBy = useCanEdit();
  const [detail, setDetail] = useState<StudyPlanDetail | null>(null);
  const ask = useConfirm();
  const [model, setModel] = useState<PlanModel | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [planState, setPlanState] = useState<SaveState>("idle");
  const [childState, setChildState] = useState<SaveState>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [settings, setSettings] = useState(false);
  // Số test của từng đề (nhãn "No tests"); đọc lại khi rời đề để thấy test vừa thêm ở panel.
  const [tests, setTests] = useState<Map<number, number>>(new Map());
  const requested = useRef(new Set<number>());
  const loadTests = useCallback((id: number) => {
    requested.current.add(id);
    fetchProblem(String(id)).then((p) => setTests((m) => new Map(m).set(id, p.testCaseCount ?? 0)),
      () => requested.current.delete(id));
  }, []);
  useEffect(() => {
    model?.sessions.forEach((s) => s.entries.forEach((e) => {
      if (e.problem && !requested.current.has(e.problem.problemId)) loadTests(e.problem.problemId);
    }));
  }, [model, loadTests]);
  const savedSig = useRef("");
  const detailRef = useRef<StudyPlanDetail | null>(null);
  const modelRef = useRef<PlanModel | null>(null);
  detailRef.current = detail;
  modelRef.current = model;

  // Lựa chọn giữ ở state; URL chỉ phản chiếu (?node=) — key session / mục sinh lại mỗi lần tải nên mở lại thì chọn session đầu.
  const [selected, setSelected] = useState<NodeId>("none");
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  // Đổi URL bằng history API: router.replace sang slug khác sẽ remount trang và làm mất thay đổi chưa lưu.
  const select = useCallback((id: NodeId) => {
    const prev = selectedRef.current;
    const left = prev.startsWith("entry:") && modelRef.current?.sessions.flatMap((s) => s.entries).find((e) => `entry:${e.key}` === prev);
    if (left && left.problem && prev !== id) loadTests(left.problem.problemId);
    setSelected(id);
    const current = detailRef.current?.slug ?? slug;
    window.history.replaceState(null, "", `/admin/study-plans/${current}?node=${encodeURIComponent(id)}`);
  }, [slug, loadTests]);

  useEffect(() => {
    fetchStudyPlan(slug)
      .then((p) => {
        setDetail(p);
        const m = fromDetail(p);
        setModel(m);
        savedSig.current = signature(m);
        setSelected(m.sessions[0] ? `session:${m.sessions[0].key}` : "none");
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [slug, loadTests]);

  const canEdit = !!detail && canEditBy(detail.createdById);
  const dirty = !!model && signature(model) !== savedSig.current;
  const live = detail?.status === "PUBLISHED";

  /** Lưu plan ngay (autosave và trước khi publish). */
  const savePlan = useCallback(async () => {
    const m = modelRef.current;
    const d = detailRef.current;
    if (!m || !d) return;
    const sig = signature(m);
    if (sig === savedSig.current) return;
    setPlanState("saving");
    try {
      const saved = await updateStudyPlan(d.slug, toDraft(m, d.updatedAt));
      savedSig.current = sig;
      setDetail(saved);
      setModel((cur) => (cur ? mergeStatuses(cur, saved) : cur));
      setPlanState("saved");
      setSaveError(null);
      if (saved.slug !== d.slug) {
        detailRef.current = saved;
        window.history.replaceState(null, "", `/admin/study-plans/${saved.slug}?node=${encodeURIComponent(selectedRef.current)}`);
      }
    } catch (e) {
      setPlanState("error");
      setSaveError(errorMessage(e));
      throw e;
    }
  }, []);

  // Autosave plan: 1 s sau lần sửa cuối.
  const sig = model ? signature(model) : "";
  useEffect(() => {
    if (!model || !canEdit || sig === savedSig.current || planState === "saving") return;
    const t = setTimeout(() => void savePlan().catch(() => undefined), 1000);
    return () => clearTimeout(t);
  }, [sig, canEdit, planState, model, savePlan]);

  // Chặn rời trang khi còn thay đổi chưa lưu.
  const pending = dirty || planState === "saving" || childState === "saving" || childState === "invalid";
  useEffect(() => {
    if (!pending) return;
    const beforeUnload = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    const onClick = (e: MouseEvent) => {
      const a = (e.target as HTMLElement).closest("a");
      if (!a || a.target === "_blank" || !a.href || a.origin !== window.location.origin) return;
      if (a.pathname === window.location.pathname) return;
      e.preventDefault();
      e.stopPropagation();
      const href = a.href;
      void ask({ title: "Leave without saving?", description: "Your latest changes have not been saved yet.", action: "Leave", destructive: true })
        .then((ok) => { if (ok) window.location.assign(href); });
    };
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", onClick, true);
    return () => {
      window.removeEventListener("beforeunload", beforeUnload);
      document.removeEventListener("click", onClick, true);
    };
  }, [pending, ask]);

  if (loadError) return <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{loadError}</div>;
  if (!detail || !model) return <div className="mx-auto max-w-container px-4 py-6 text-mute">Loading study plan…</div>;

  // Cập nhật theo hàm: callback bất đồng bộ (đề / topic vừa lưu) không được ghi đè thay đổi mới hơn.
  const update = (m: PlanModel) => setModel(m);
  const patchSession = (key: string, patch: Partial<SessionNode>) => setModel((cur) => cur && ({
    ...cur, sessions: cur.sessions.map((s) => (s.key === key ? { ...s, ...patch } : s)) }));
  const patchEntries = (fn: (e: EntryNode) => EntryNode) => setModel((cur) => cur && ({
    ...cur, sessions: cur.sessions.map((s) => ({ ...s, entries: s.entries.map(fn) })) }));

  const addSession = () => {
    const session: SessionNode = { key: newKey(), title: `Session ${model.sessions.length + 1}`, description: "", entries: [] };
    update({ ...model, sessions: [...model.sessions, session] });
    select(`session:${session.key}`);
  };

  const addEntry = (sessionKey: string, entry: Omit<EntryNode, "key">) => {
    const key = newKey();
    setModel((cur) => cur && ({ ...cur, sessions: cur.sessions.map((s) => (s.key === sessionKey ? { ...s, entries: [...s.entries, { ...entry, key }] } : s)) }));
    select(`entry:${key}`);
  };

  const remove = async (id: NodeId) => {
    const n = parseNode(id);
    if (n.kind === "session") {
      const session = model.sessions.find((s) => s.key === n.key);
      const learners = session?.entries.reduce((sum, e) => sum + (e.problem?.learners ?? 0), 0) ?? 0;
      if (!(await ask({ title: `Remove "${session?.title || "this session"}"?`, description: `${learners ? `${learners} learners have submissions on it. ` : ""}Its lessons and problems are kept.`, action: "Remove", destructive: true }))) return;
      update({ ...model, sessions: model.sessions.filter((s) => s.key !== n.key) });
      select("none");
    } else if (n.kind === "entry") {
      const found = findEntry(model, n.key);
      if (!found) return;
      const { entry, session } = found;
      const learners = entry.problem?.learners ?? 0;
      if (entry.problem && !(await ask({ title: `Remove "${entryLabel(entry)}"?`, description: `${learners ? `${learners} learners are on it. ` : ""}The problem itself is kept.`, action: "Remove", destructive: true }))) return;
      update({ ...model, sessions: model.sessions.map((s) => ({ ...s, entries: s.entries.filter((e) => e.key !== n.key) })) });
      select(`session:${session.key}`);
    }
  };

  /** Nút outline ứng với một lỗi publish. */
  const issueNode = (issue: PublishIssue): NodeId | null => {
    if (issue.node === "item" && issue.itemIndex != null && model.sessions[issue.itemIndex]) return `session:${model.sessions[issue.itemIndex].key}`;
    const e = model.sessions.flatMap((s) => s.entries).find((x) =>
      (issue.node === "stage" && x.problem?.problemId === issue.problemId) || (issue.node === "topic" && x.topic?.id === issue.topicId));
    return e ? `entry:${e.key}` : null;
  };
  const goTo = (issue: PublishIssue) => {
    setPublishing(false);
    const id = issueNode(issue);
    if (id) select(id);
  };

  const doPublish = async () => {
    const published = await publishStudyPlan(detail.slug);
    setDetail(published);
    setModel((cur) => (cur ? mergeStatuses(cur, published) : cur));
    setPublishing(false);
    toast("Published — learners can see this plan", "success");
  };

  const transition = async (status: StudyPlanStatus) => {
    try {
      const d = await transitionStudyPlan(detail.slug, status);
      setDetail(d);
      setModel((cur) => (cur ? mergeStatuses(cur, d) : cur));
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const onProblem = (p: ProblemFull) => patchEntries((e) => (e.problem?.problemId === p.id
    ? { ...e, problem: { ...e.problem, title: p.title, slug: p.slug, difficulty: p.difficulty, status: p.status ?? null } } : e));
  const onTopicSaved = (t: AuthoringTopic) => patchEntries((e) => (e.topic?.id === t.id
    ? { ...e, topic: { id: t.id, slug: t.slug, title: t.title, status: t.status, stepCount: t.steps.length } } : e));
  // Nhãn cảnh báo trên outline, tính ngay từ dữ liệu đang có.
  const issues = new Map<NodeId, string>();
  model.sessions.forEach((s) => {
    s.entries.forEach((e) => {
      if (e.topic?.stepCount === 0) issues.set(`entry:${e.key}`, "Empty");
      if (e.problem && tests.get(e.problem.problemId) === 0) issues.set(`entry:${e.key}`, "No tests");
    });
  });
  const usedProblems = new Set(model.sessions.flatMap((s) => s.entries.flatMap((e) => (e.problem ? [e.problem.problemId] : []))));

  // ── Cột phải ──────────────────────────────────────────────────────────
  const node = parseNode(selected);
  let panel: React.ReactNode = null;
  if (node.kind === "session") {
    const session = model.sessions.find((s) => s.key === node.key);
    if (session) panel = (
      <fieldset disabled={!canEdit} className="space-y-4">
        <label className={`block ${field}`}>Session title
          <Input value={session.title} onChange={(e) => patchSession(session.key, { title: e.target.value })} />
        </label>
        <div className={`block ${field}`}>Description
          <RichTextEditor variant="compact" label="Session description" disabled={!canEdit} className="mt-1"
            value={session.description} onChange={(description) => patchSession(session.key, { description })} />
        </div>
        <p className="text-body-sm text-mute">{session.entries.length} item{session.entries.length === 1 ? "" : "s"}. Use “+” next to the session name to add lessons and problems.</p>
      </fieldset>
    );
  } else if (node.kind === "entry") {
    const found = findEntry(model, node.key);
    const entry = found?.entry;
    if (entry?.topic) {
      panel = <TopicEditor key={entry.key} slug={entry.topic.slug} embedded onSaveState={setChildState} onSaved={onTopicSaved} />;
    } else if (found && entry?.problem) {
      const stage: StageNode = entry.problem;
      const label = entry.type === "STAGE" ? `${found.session.title} · stage` : "Problem";
      panel = <StagePanel key={entry.key} stage={stage} label={label} live={live} canEdit={canEdit} onProblem={onProblem} onSaveState={setChildState} />;
    }
  } else if (node.kind === "add" && canEdit) {
    const session = model.sessions.find((s) => s.key === node.key);
    if (session) panel = (
      <AddPanel key={selected} mode={node.mode} session={session.title} exclude={usedProblems}
        onTopic={(topic: TopicNode) => addEntry(session.key, { type: "LESSON", topic })}
        onProblem={(p) => addEntry(session.key, { type: p.type === "OOP_GRINDING" ? "STAGE" : "PROBLEM", problem: {
          problemId: p.id, slug: p.slug, title: p.title, difficulty: p.difficulty, status: p.status ?? "DRAFT", learners: 0 } })} />
    );
  }
  if (!panel) panel = <p className="text-body-sm text-mute">Select a session or an item in the outline.</p>;

  const stageCount = usedProblems.size;
  const unlocked = model.sessions.flatMap((s) => s.entries).filter((e) => e.problem && e.problem.status !== "PUBLISHED").length;
  const state = planState === "saving" || childState === "saving" ? "saving"
    : planState === "error" || childState === "error" ? "error" : childState === "invalid" ? "invalid" : dirty ? "pending" : "saved";

  return (
    <div className="mx-auto flex w-full max-w-container flex-col gap-4 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-center gap-3">
        <Link href="/admin/study-plans" className="text-body-sm text-link hover:underline">← Study plans</Link>
        <h1 className="min-w-0 truncate text-heading-md text-ink">{model.title}</h1>
        <Badge variant={PLAN_STATUS_VARIANT[detail.status]}>{detail.status}</Badge>
        <span className="text-body-sm text-mute" role="status" aria-live="polite">
          {state === "saving" && <span className="flex items-center gap-1"><Loader2 className="h-3.5 w-3.5 animate-spin" />Saving…</span>}
          {state === "saved" && "All changes saved"}
          {state === "pending" && "Unsaved changes"}
          {state === "invalid" && "Fix the highlighted fields to save"}
          {state === "error" && <>Save failed{saveError ? `: ${saveError}` : ""} · <button type="button" className="text-link underline"
            onClick={() => (saveError?.includes("another tab") ? window.location.reload() : void savePlan().catch(() => undefined))}>
            {saveError?.includes("another tab") ? "Reload" : "Retry"}</button></>}
          {issues.size > 0 && ` · ${issues.size} issue${issues.size === 1 ? "" : "s"}`}
        </span>
        <span className="ml-auto flex items-center gap-2">
          <Button asChild size="sm" variant="outline"><Link href={`/study-plans/${detail.slug}`}><Eye className="h-4 w-4" />Preview</Link></Button>
          <Button size="sm" variant="outline" onClick={() => setSettings(true)}><Settings className="h-4 w-4" />Plan settings</Button>
        </span>
      </header>
      {!canEdit && (
        <p role="note" className="rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-mute">
          Read-only: only {detail.createdByUsername ?? "the author"} or an admin can change this plan.</p>
      )}
      {live && (
        <p role="status" className="rounded-sm border border-amber-300 bg-amber-50 px-3 py-2 text-body-sm text-amber-900">
          Live · {detail.learnerCount ?? 0} learner{detail.learnerCount === 1 ? "" : "s"}. Changes are visible immediately.
          {unlocked > 0 && canEdit && <> {unlocked} problem{unlocked === 1 ? "" : "s"} unlocked · <button type="button" className="font-medium underline"
            onClick={() => setPublishing(true)}>Publish changes</button></>}
        </p>
      )}

      <div className="grid gap-6 md:grid-cols-[300px_minmax(0,1fr)]">
        <aside className="md:sticky md:top-4 md:self-start">
          <Outline model={model} selected={selected} issues={issues} canEdit={canEdit} onSelect={select} onChange={update}
            onAddSession={addSession} onRemove={remove} />
        </aside>
        <section className="min-w-0 rounded-sm border border-border bg-card p-5" aria-label="Editor">
          {panel}
        </section>
      </div>

      {settings && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/20 p-4" role="dialog" aria-modal="true" aria-label="Plan settings"
          onKeyDown={(e) => e.key === "Escape" && setSettings(false)}>
          <div className="w-full max-w-lg space-y-4 rounded-sm border border-border bg-background p-5 shadow-lg">
            <div className="flex items-center justify-between">
              <h2 className="text-heading-md text-ink">Plan settings</h2>
              <Button size="icon-sm" variant="ghost" aria-label="Close" onClick={() => setSettings(false)}><X className="h-4 w-4" /></Button>
            </div>
            <fieldset disabled={!canEdit} className="space-y-4">
              <label className={`block ${field}`}>Title
                <Input autoFocus value={model.title} onChange={(e) => update({ ...model, title: e.target.value })} />
              </label>
              <p className="text-body-sm text-mute">URL: <span className="font-mono text-ink">/study-plans/{detail.slug}</span>
                {!live && " · follows the title"}</p>
              <div className={`block ${field}`}>Description
                <RichTextEditor variant="compact" label="Description" disabled={!canEdit} className="mt-1"
                  value={model.description} onChange={(description) => update({ ...model, description })} />
              </div>
            </fieldset>
            {canEdit && (
              <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
                <span className="text-body-sm text-mute">Status:</span><Badge variant={PLAN_STATUS_VARIANT[detail.status]}>{detail.status}</Badge>
                <span className="ml-auto flex gap-2">
                  {live && <Button size="sm" variant="outline" onClick={() => transition("DRAFT")}><FileEdit className="h-4 w-4" />Move to draft</Button>}
                  {live && <Button size="sm" variant="outline" onClick={() => transition("ARCHIVED")}><Archive className="h-4 w-4" />Archive</Button>}
                  {(!live || unlocked > 0) && (
                    <Button size="sm" onClick={() => { setSettings(false); setPublishing(true); }}>
                      <Rocket className="h-4 w-4" />{live ? "Publish changes" : "Publish…"}</Button>
                  )}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {publishing && (
        <PublishDialog slug={detail.slug} title={model.title} itemCount={model.sessions.length} stageCount={stageCount}
          beforeCheck={async () => {
            await savePlan();
            return detailRef.current?.slug ?? detail.slug;
          }}
          onChecked={() => undefined}
          onGoTo={goTo} onPublish={doPublish} onClose={() => setPublishing(false)} />
      )}
    </div>
  );
}
