"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronDown, Loader2, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useUser } from "@/hooks/use-user";
import {
  cloneProblem, createProblem, fetchStudyPlan, publishStudyPlan, transitionStudyPlan, updateStudyPlan,
} from "@/lib/services";
import type { ProblemFull, PublishIssue, StudyPlanDetail, StudyPlanStatus } from "@/types/api";
import { AdminNav } from "../admin-nav";
import { ProblemPicker, slugify } from "../admin-study-plans";
import { TopicEditor, type SaveState } from "../admin-topics";
import {
  findStage, fromDetail, newKey, parseNode, signature, toDraft,
  type ItemNode, type NodeId, type PlanModel, type StageNode, type TopicNode,
} from "./model";
import { Outline } from "./outline";
import { PublishDialog } from "./publish-dialog";
import { StagePanel } from "./stage-panel";
import { useConfirm } from "@/components/ui/confirm";

/* /admin/study-plans/[slug] — Course Builder (specs/course-builder.spec.md). */

const field = "space-y-1 text-body-sm text-mute";
const STATUS_VARIANT: Record<StudyPlanStatus, "success" | "secondary" | "outline"> = { PUBLISHED: "success", DRAFT: "secondary", ARCHIVED: "outline" };

function ago(t: number | null, now: number) {
  if (!t) return "";
  const s = Math.max(0, Math.round((now - t) / 1000));
  return s < 60 ? `${s}s ago` : `${Math.round(s / 60)}m ago`;
}

/** Áp trạng thái đề / topic mới từ server lên model mà giữ key của item (lựa chọn trên outline không đổi). */
function mergeStatuses(model: PlanModel, detail: StudyPlanDetail): PlanModel {
  const fresh = fromDetail(detail);
  const stages = new Map(fresh.items.flatMap((it) => it.stages).map((s) => [s.problemId, s]));
  return {
    ...model, status: fresh.status,
    items: model.items.map((it) => ({
      ...it,
      stages: it.stages.map((s) => {
        const f = stages.get(s.problemId);
        if (!f) return s;
        const topicStatus = new Map(f.topics.map((t) => [t.id, t.status]));
        return { ...s, status: f.status, learners: f.learners, title: f.title ?? s.title,
          topics: s.topics.map((t) => ({ ...t, status: topicStatus.get(t.id) ?? t.status })) };
      }),
    })),
  };
}

export function CourseBuilder({ slug }: { slug: string }) {
  const params = useSearchParams();
  const { user } = useUser();
  const [detail, setDetail] = useState<StudyPlanDetail | null>(null);
  const ask = useConfirm();
  const [model, setModel] = useState<PlanModel | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [planState, setPlanState] = useState<SaveState>("idle");
  const [childState, setChildState] = useState<SaveState>("idle");
  const [savedAt, setSavedAt] = useState<number | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [publishing, setPublishing] = useState(false);
  const [issues, setIssues] = useState<Set<NodeId>>(new Set());
  const [focusTopics, setFocusTopics] = useState(0);
  const [slugTouched, setSlugTouched] = useState(false);
  const savedSig = useRef("");
  const detailRef = useRef<StudyPlanDetail | null>(null);
  const modelRef = useRef<PlanModel | null>(null);
  detailRef.current = detail;
  modelRef.current = model;

  const selected: NodeId = params.get("node") ?? "plan";
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  // Đổi URL bằng history API (Next 14.2 đồng bộ useSearchParams): router.replace sang slug khác sẽ remount trang và
  // tải lại plan từ server, làm mất thay đổi chưa lưu. Slug có thể đổi khi lưu ⇒ luôn dựng từ slug hiện tại.
  const select = useCallback((id: NodeId) => {
    const current = detailRef.current?.slug ?? slug;
    window.history.replaceState(null, "", `/admin/study-plans/${current}?node=${encodeURIComponent(id)}`);
  }, [slug]);

  useEffect(() => {
    fetchStudyPlan(slug)
      .then((p) => {
        setDetail(p);
        const m = fromDetail(p);
        setModel(m);
        savedSig.current = signature(m);
        setSlugTouched(!p.slug.startsWith("untitled-"));
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [slug]);

  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 5000); return () => clearInterval(t); }, []);

  const canEdit = !!detail && !!user && (user.role === "ADMIN" || detail.createdById === user.id);
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
      setSavedAt(Date.now());
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

  const childSaveState = useCallback((s: SaveState) => {
    setChildState(s);
    if (s === "saved") setSavedAt(Date.now());
  }, []);

  if (loadError) return <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{loadError}</div>;
  if (!detail || !model) return <div className="mx-auto max-w-container px-4 py-6 text-mute">Loading study plan…</div>;

  // Cập nhật theo hàm: callback bất đồng bộ (đề / topic vừa lưu) không được ghi đè thay đổi mới hơn.
  const update = (m: PlanModel) => setModel(m);
  const patchItem = (key: string, patch: Partial<ItemNode>) => setModel((cur) => cur && ({
    ...cur, items: cur.items.map((it) => (it.key === key ? { ...it, ...patch } : it)) }));
  const patchStage = (problemId: number, patch: Partial<StageNode>) => setModel((cur) => cur && ({
    ...cur, items: cur.items.map((it) => ({ ...it, stages: it.stages.map((s) => (s.problemId === problemId ? { ...s, ...patch } : s)) })) }));

  const addItem = (kind: ItemNode["kind"]) => {
    const item: ItemNode = { key: newKey(), kind, title: "", description: "", stages: [] };
    update({ ...model, items: [...model.items, item] });
    select(`item:${item.key}`);
  };

  const appendStage = (item: ItemNode, p: ProblemFull) => {
    const stage: StageNode = { problemId: p.id, slug: p.slug, title: p.title, difficulty: p.difficulty,
      status: (p.status ?? "DRAFT"), learners: 0, topics: [] };
    setModel((cur) => cur && ({ ...cur, items: cur.items.map((it) => (it.key === item.key ? { ...it, stages: [...it.stages, stage] } : it)) }));
    select(`stage:${p.id}`);
  };

  /** + stage: nhân bản stage cuối (B3); project trống ⇒ hỏi đề trống / đề có sẵn. */
  const addStage = async (item: ItemNode) => {
    const last = item.stages[item.stages.length - 1];
    if (!last) {
      select(`item:${item.key}`);
      return;
    }
    try {
      const n = item.stages.length + 1;
      const title = `${(item.title || last.title || "Stage").replace(/\s+\d+\/\d+.*$/, "")} ${n}`;
      appendStage(item, await cloneProblem(last.problemId, title));
      toast(`Stage ${n} created from stage ${n - 1}`, "success");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const blankStage = async (item: ItemNode) => {
    try {
      const n = item.stages.length + 1;
      appendStage(item, await createProblem({
        title: item.kind === "PROJECT" ? `${item.title || "Project"} ${n}` : item.title || "Practice problem",
        description: "Describe the task.", difficulty: "EASY", timeLimitMs: 1000, memoryLimitMb: 256, tags: [], type: "OOP_GRINDING",
        starterTemplates: null,
      }));
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const remove = async (id: NodeId) => {
    const n = parseNode(id);
    if (n.kind === "item") {
      const item = model.items.find((it) => it.key === n.key);
      const learners = item?.stages.reduce((s, x) => s + x.learners, 0) ?? 0;
      if (!(await ask({ title: `Remove "${item?.title || "this item"}"?`, description: `${learners ? `${learners} learners have submissions on it. ` : ""}Its problems are kept.`, action: "Remove", destructive: true }))) return;
      update({ ...model, items: model.items.filter((it) => it.key !== n.key) });
      select("plan");
    } else if (n.kind === "stage") {
      const found = findStage(model, n.problemId);
      if (!found) return;
      const { stage, item } = found;
      if (!(await ask({ title: `Remove "${stage.title}"?`, description: `${stage.learners ? `${stage.learners} learners are on this stage. ` : ""}The problem itself is kept.`, action: "Remove", destructive: true }))) return;
      update({ ...model, items: model.items.map((it) => ({ ...it, stages: it.stages.filter((s) => s.problemId !== n.problemId) })) });
      select(`item:${item.key}`);
    } else if (n.kind === "topic") {
      const found = findStage(model, n.problemId);
      if (found) patchStage(n.problemId, { topics: found.stage.topics.filter((t) => t.id !== n.topicId) });
      select(`stage:${n.problemId}`);
    }
  };

  const goTo = (issue: PublishIssue) => {
    setPublishing(false);
    if (issue.node === "item" && issue.itemIndex != null && model.items[issue.itemIndex]) select(`item:${model.items[issue.itemIndex].key}`);
    else if (issue.node === "stage" && issue.problemId) select(`stage:${issue.problemId}`);
    else if (issue.node === "topic" && issue.topicId) {
      const st = model.items.flatMap((it) => it.stages).find((s) => s.topics.some((t) => t.id === issue.topicId));
      if (st) select(`topic:${issue.topicId}@${st.problemId}`);
    }
  };

  const markIssues = (errors: PublishIssue[]) => {
    const ids = new Set<NodeId>();
    errors.forEach((e) => {
      if (e.node === "plan") ids.add("plan");
      if (e.node === "item" && e.itemIndex != null && model.items[e.itemIndex]) ids.add(`item:${model.items[e.itemIndex].key}`);
      if (e.node === "stage" && e.problemId) ids.add(`stage:${e.problemId}`);
      if (e.node === "topic" && e.topicId) {
        model.items.flatMap((it) => it.stages).filter((s) => s.topics.some((t) => t.id === e.topicId))
          .forEach((s) => ids.add(`topic:${e.topicId}@${s.problemId}`));
      }
    });
    setIssues(ids);
  };

  const doPublish = async () => {
    const published = await publishStudyPlan(detail.slug);
    setDetail(published);
    setModel((cur) => (cur ? mergeStatuses(cur, published) : cur));
    setIssues(new Set());
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

  const onProblem = (p: ProblemFull) => patchStage(p.id, { title: p.title, slug: p.slug, difficulty: p.difficulty, status: p.status ?? null });
  const onTopicSaved = (problemId: number) => (t: { id: number; slug: string; title: string; status: StudyPlanStatus }) => {
    const found = findStage(modelRef.current ?? model, problemId);
    if (found) patchStage(problemId, { topics: found.stage.topics.map((x) => (x.id === t.id ? { ...x, title: t.title, slug: t.slug, status: t.status } : x)) });
  };

  // ── Cột phải ──────────────────────────────────────────────────────────
  const node = parseNode(selected);
  let panel: React.ReactNode = null;
  let heading = "";
  if (node.kind === "plan") {
    heading = "Plan";
    panel = (
      <fieldset disabled={!canEdit} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className={field}>Title
            <Input value={model.title} onChange={(e) => update({ ...model, title: e.target.value,
              slug: slugTouched || live ? model.slug : slugify(e.target.value) || model.slug })} />
          </label>
          <label className={field}>Slug (URL)
            <Input value={model.slug} className="font-mono" onChange={(e) => { setSlugTouched(true); update({ ...model, slug: e.target.value }); }} />
          </label>
        </div>
        <label className={`block ${field}`}>Description
          <Textarea rows={3} value={model.description} onChange={(e) => update({ ...model, description: e.target.value })} />
        </label>
      </fieldset>
    );
  } else if (node.kind === "item") {
    const item = model.items.find((it) => it.key === node.key);
    if (item) {
      heading = item.kind === "PROJECT" ? "Project" : "Practice problem";
      panel = (
        <fieldset disabled={!canEdit} className="space-y-4">
          <label className={`block ${field}`}>Title
            <Input value={item.title} onChange={(e) => patchItem(item.key, { title: e.target.value })} />
          </label>
          <label className={`block ${field}`}>Description
            <Textarea rows={2} value={item.description} onChange={(e) => patchItem(item.key, { description: e.target.value })} />
          </label>
          {item.stages.length === 0 && (
            <section className="space-y-2 rounded-sm border border-border p-3" aria-label="First stage">
              <p className="text-body-sm text-ink">{item.kind === "PROJECT" ? "Add the first stage" : "Choose the problem"}</p>
              <Button size="sm" variant="outline" onClick={() => blankStage(item)}><Plus className="h-4 w-4" />Blank problem</Button>
              <ProblemPicker exclude={new Set(model.items.flatMap((it) => it.stages.map((s) => s.problemId)))}
                onPick={(p) => appendStage(item, p)} />
            </section>
          )}
          {item.kind === "PROJECT" && item.stages.length > 0 && (
            <p className="text-body-sm text-mute">{item.stages.length} stage{item.stages.length === 1 ? "" : "s"}.
              {" "}<button type="button" className="text-link hover:underline" onClick={() => addStage(item)}>Add a stage</button> copies the last one.</p>
          )}
        </fieldset>
      );
    }
  } else if (node.kind === "stage") {
    const found = findStage(model, node.problemId);
    if (found) {
      const { item, index, stage } = found;
      const label = item.kind === "PROJECT" ? `Stage ${index + 1}` : "Problem";
      heading = `${label} · ${stage.title ?? ""}`;
      panel = (
        <StagePanel key={stage.problemId} stage={stage} label={label} live={live} canEdit={canEdit}
          onProblem={onProblem} onSaveState={childSaveState} focusTopics={focusTopics}
          onTopics={(topics: TopicNode[]) => patchStage(stage.problemId, { topics })}
          onOpenTopic={(t) => select(`topic:${t.id}@${stage.problemId}`)} />
      );
    }
  } else if (node.kind === "topic") {
    const found = findStage(model, node.problemId);
    const topic = found?.stage.topics.find((t) => t.id === node.topicId);
    if (topic) {
      heading = `Topic · ${topic.title}`;
      panel = <TopicEditor key={topic.id} slug={topic.slug} embedded onSaveState={childSaveState} onSaved={onTopicSaved(node.problemId)} />;
    }
  }
  if (!panel) {
    heading = "Not found";
    panel = <p className="text-body-sm text-mute">This node no longer exists. <button type="button" className="text-link" onClick={() => select("plan")}>Back to the plan</button></p>;
  }

  const stageCount = model.items.reduce((n, it) => n + it.stages.length, 0);
  const unlocked = model.items.flatMap((it) => it.stages).filter((s) => s.status !== "PUBLISHED").length;
  const state = planState === "saving" || childState === "saving" ? "saving"
    : planState === "error" || childState === "error" ? "error" : childState === "invalid" ? "invalid" : dirty ? "pending" : "saved";

  return (
    <div className="mx-auto flex w-full max-w-container flex-col gap-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <AdminNav />
      <header className="flex flex-wrap items-center gap-3">
        <Link href="/admin/study-plans" className="text-body-sm text-link hover:underline">← Study plans</Link>
        <Badge variant={STATUS_VARIANT[detail.status]}>{detail.status}</Badge>
      </header>
      {!canEdit && (
        <p role="note" className="rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-mute">
          Read-only: only {detail.createdByUsername ?? "the author"} or an admin can change this plan.</p>
      )}
      {live && (
        <p role="status" className="rounded-sm border border-amber-300 bg-amber-50 px-3 py-2 text-body-sm text-amber-900">
          Live · {detail.learnerCount ?? 0} learner{detail.learnerCount === 1 ? "" : "s"}. Changes are visible immediately.
          {unlocked > 0 && <> {unlocked} stage{unlocked === 1 ? "" : "s"} unlocked · <button type="button" className="font-medium underline"
            onClick={() => setPublishing(true)}>Publish changes</button></>}
        </p>
      )}

      <div className="grid gap-6 md:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="rounded-sm border border-border bg-card p-2 md:sticky md:top-4 md:self-start">
          <Outline model={model} selected={selected} issues={issues} onSelect={select} onChange={update}
            onAddItem={addItem} onAddStage={addStage} onRemove={remove}
            onAddTopic={(stage) => { select(`stage:${stage.problemId}`); setFocusTopics((n) => n + 1); }} />
        </aside>
        <section className="min-w-0 rounded-sm border border-border bg-card p-5" aria-label={`Editor: ${heading}`}>
          {panel}
        </section>
      </div>

      <footer className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex max-w-container flex-wrap items-center gap-3 px-4 py-2 sm:px-6 lg:px-8">
          <span className="text-body-sm text-mute" role="status" aria-live="polite">
            {state === "saving" && <span className="flex items-center gap-1"><Loader2 className="h-3.5 w-3.5 animate-spin" />Saving…</span>}
            {state === "saved" && (savedAt ? `Saved ${ago(savedAt, now)}` : "All changes saved")}
            {state === "pending" && "Unsaved changes"}
            {state === "invalid" && "Fix the highlighted fields to save"}
            {state === "error" && <>Save failed{saveError ? `: ${saveError}` : ""} · <button type="button" className="text-link underline"
              onClick={() => (saveError?.includes("another tab") ? window.location.reload() : void savePlan().catch(() => undefined))}>
              {saveError?.includes("another tab") ? "Reload" : "Retry"}</button></>}
            {issues.size > 0 && ` · ${issues.size} issue${issues.size === 1 ? "" : "s"}`}
          </span>
          <span className="ml-auto flex items-center gap-2">
            <Link href={`/study-plans/${detail.slug}`} className="text-body-sm text-link hover:underline">Preview</Link>
            {canEdit && (live ? (
              <>
                {unlocked > 0 ? <Button size="sm" onClick={() => setPublishing(true)}>Publish changes</Button>
                  : <span className="text-body-sm text-solved">Published</span>}
                <details className="relative">
                  <summary className="flex cursor-pointer list-none items-center gap-1 rounded-sm border border-border px-2 py-1 text-body-sm">
                    Unpublish <ChevronDown className="h-3.5 w-3.5" /></summary>
                  <div className="absolute bottom-full right-0 mb-1 w-40 rounded-sm border border-border bg-card py-1 shadow">
                    <button type="button" className="block w-full px-3 py-1.5 text-left text-body-sm hover:bg-hairline-soft" onClick={() => transition("DRAFT")}>Move to draft</button>
                    <button type="button" className="block w-full px-3 py-1.5 text-left text-body-sm hover:bg-hairline-soft" onClick={() => transition("ARCHIVED")}>Archive</button>
                  </div>
                </details>
              </>
            ) : (
              <Button size="sm" onClick={() => setPublishing(true)}>Publish…</Button>
            ))}
          </span>
        </div>
      </footer>

      {publishing && (
        <PublishDialog slug={detail.slug} title={model.title || "Untitled plan"} itemCount={model.items.length} stageCount={stageCount}
          beforeCheck={async () => {
            await savePlan();
            return detailRef.current?.slug ?? detail.slug;
          }}
          onChecked={(c) => markIssues(c.errors)}
          onGoTo={goTo} onPublish={doPublish} onClose={() => setPublishing(false)} />
      )}
    </div>
  );
}
