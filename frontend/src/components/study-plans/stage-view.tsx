"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, Check, ChevronDown, Lock } from "lucide-react";
import { CodeExercise } from "@/components/code-exercise/code-exercise";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { stageName, topicHref } from "@/components/topics/stage-timeline";
import { Markdown } from "@/components/ui/markdown";
import { Button } from "@/components/ui/button";
import { useLoad } from "@/hooks/use-load";
import { fetchStudyPlan } from "@/lib/services";
import { cn } from "@/lib/utils";
import theme from "@/components/layout/plex-theme.module.css";
import { IdeButton } from "./ide-button";
import v from "./stage-view.module.css";

/**
 * /study-plans/[slug]/[itemId]/[stageNo] — trang làm một stage (specs/practice-ide-rich-editor.spec.md §3):
 * đề + khu làm bài (bài một file ngay trong trang; project nhiều file mở bằng IntelliJ) + chủ đề của stage.
 */
export function StageView({ slug, itemId, stageNo }: { slug: string; itemId: number; stageNo: number }) {
  const [attempt, setAttempt] = useState(0);
  const load = useLoad(() => fetchStudyPlan(slug), [slug, attempt]);
  const [showProject, setShowProject] = useState(false);
  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  const [cachedPlan, setCachedPlan] = useState<Awaited<ReturnType<typeof fetchStudyPlan>> | null>(null);
  useEffect(() => {
    if (load.state === "ok") setCachedPlan(load.data);
  }, [load]);

  const plan = load.state === "ok" ? load.data : cachedPlan;
  const item = plan?.items.find((it) => it.id === itemId);
  const stage = item?.stages.find((st) => st.stageNo === stageNo);
  const planHref = plan ? `/study-plans/${plan.slug}` : "/study-plans";

  if (load.state === "loading" && !plan) return <div className={cn(theme.theme, v.page)} aria-busy="true"><p className={v.muted}>Loading stage…</p></div>;
  if (load.state === "error") {
    return (
      <div className={cn(theme.theme, v.page)}>
        <p role="alert" className={v.error}>{load.message}</p>
        <button type="button" className={v.secondary} onClick={reload}>Try again</button>
      </div>
    );
  }
  if (!plan || !item || !stage) {
    return (
      <div className={cn(theme.theme, v.page)}>
        <p role="alert" className={v.error}>This stage does not exist any more.</p>
        <Link href={planHref} className={v.secondary}>Back to {plan?.title ?? "Study plans"}</Link>
      </div>
    );
  }

  if (stage.status === "LOCKED") {
    const active = item.stages.find((st) => st.status !== "DONE" && st.status !== "LOCKED") ?? item.stages[0];
    return (
      <div className={cn(theme.theme, v.page)}>
        <nav className={v.crumbs} aria-label="Breadcrumb">
          <Link href="/study-plans">Study plans</Link> → <Link href={planHref}>{plan.title}</Link>
        </nav>
        <section className={v.card} aria-label="Stage">
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <div className="mb-3 rounded-full bg-amber-500/10 p-3 text-amber-600 dark:text-amber-500">
              <Lock size={24} aria-hidden />
            </div>
            <h2 className="text-lg font-semibold text-ink">Stage {stage.stageNo} is locked</h2>
            <p className="mt-1 max-w-md text-sm text-mute">
              Finish stage {stage.stageNo - 1} first.
            </p>
            {active && (
              <Button asChild className="mt-4">
                <Link href={`/study-plans/${plan.slug}/${item.id}/${active.stageNo}`}>
                  Go to Stage {active.stageNo}
                </Link>
              </Button>
            )}
          </div>
        </section>
      </div>
    );
  }

  const project = item.entries.some((e) => e.stageNo === stage.stageNo && e.type === "STAGE");
  const count = item.stages.length;
  const title = project ? `Stage ${stage.stageNo}/${count} · ${stageName(stage)}` : stage.title ?? item.title;
  const stageHref = (n: number) => `/study-plans/${plan.slug}/${item.id}/${n}`;
  const prev = item.stages.find((st) => st.stageNo === stage.stageNo - 1);
  const next = item.stages.find((st) => st.stageNo === stage.stageNo + 1);
  const topics = stage.topics ?? [];

  return (
    <div className={cn(theme.theme, v.page)}>
      <nav className={v.crumbs} aria-label="Breadcrumb">
        <Link href="/study-plans">Study plans</Link> → <Link href={planHref}>{plan.title}</Link>
        {project && <> → {item.title}</>}
      </nav>
      <div className={v.cardHead}>
        <h1 className={v.title}>{title}</h1>
        {stage.difficulty && <DifficultyBadge value={stage.difficulty} />}
        {stage.status === "DONE" && <span className={v.done}><Check size={15} aria-hidden /> Done</span>}
      </div>
      {project && item.description && (
        <>
          <button type="button" className={v.linkButton} aria-expanded={showProject} onClick={() => setShowProject((o) => !o)}>
            Project description <ChevronDown size={14} aria-hidden className={cn(showProject && v.flip)} />
          </button>
          {showProject && <div className={cn(v.card, v.prose)}><Markdown>{item.description}</Markdown></div>}
        </>
      )}

      {topics.length > 0 && (
        <section className={v.card} aria-label="Topics in stage">
          <h2 className={v.small}>Topics in stage</h2>
          <ul className={v.chips}>
            {topics.map((tp) => (
              <li key={tp.id}>
                <Link href={topicHref(tp.slug, plan.slug, stage.stageNo)} className={cn(v.chip, tp.progress === "DONE" && v.chipDone)}>
                  {tp.progress === "DONE" && <Check size={13} aria-hidden />} {tp.title}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className={v.card} aria-label="Stage">
        {!stage.published || !stage.slug ? (
          <p className={v.muted}>This stage is being updated. Try again later.</p>
        ) : (
          <CodeExercise
            problemSlug={stage.slug}
            passed={stage.status === "DONE"}
            onSolved={reload}
            ideAction={<IdeButton planSlug={plan.slug} itemId={item.id} stageNo={stage.stageNo} />}
          />
        )}
      </section>

      {project && (prev || next) && (
        <nav className={v.pager} aria-label="Stages">
          {prev ? <Link href={stageHref(prev.stageNo)} className={v.secondary}><ArrowLeft size={15} aria-hidden /> Stage {prev.stageNo}</Link> : <span />}
          {next && next.status !== "LOCKED" && (
            <Link href={stageHref(next.stageNo)} className={v.secondary}>Stage {next.stageNo} <ArrowRight size={15} aria-hidden /></Link>
          )}
        </nav>
      )}
    </div>
  );
}
