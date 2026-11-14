"use client";
import Link from "next/link";
import { useCallback, useState } from "react";
import { ArrowLeft, ArrowRight, Check, ChevronDown, Lock } from "lucide-react";
import { CodeExercise } from "@/components/code-exercise/code-exercise";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { topicHref } from "@/components/topics/stage-timeline";
import { Markdown } from "@/components/ui/markdown";
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

  if (load.state === "loading") return <div className={cn(theme.theme, v.page)} aria-busy="true"><p className={v.muted}>Loading stage…</p></div>;
  if (load.state === "error") {
    return (
      <div className={cn(theme.theme, v.page)}>
        <p role="alert" className={v.error}>{load.message}</p>
        <button type="button" className={v.secondary} onClick={reload}>Try again</button>
      </div>
    );
  }
  const plan = load.data;
  const item = plan.items.find((it) => it.id === itemId);
  const stage = item?.stages.find((st) => st.stageNo === stageNo);
  const planHref = `/study-plans/${plan.slug}`;
  if (!item || !stage) {
    return (
      <div className={cn(theme.theme, v.page)}>
        <p role="alert" className={v.error}>This stage does not exist any more.</p>
        <Link href={planHref} className={v.secondary}>Back to {plan.title}</Link>
      </div>
    );
  }

  const project = item.kind === "PROJECT";
  const count = item.stages.length;
  const title = project ? `${item.title}. Stage ${stage.stageNo}/${count}` : stage.title ?? item.title;
  const stageHref = (n: number) => `/study-plans/${plan.slug}/${item.id}/${n}`;
  const prev = item.stages.find((st) => st.stageNo === stage.stageNo - 1);
  const next = item.stages.find((st) => st.stageNo === stage.stageNo + 1);
  const topics = stage.topics ?? [];

  return (
    <div className={cn(theme.theme, v.page)}>
      <nav className={v.crumbs} aria-label="Breadcrumb">
        <Link href="/study-plans">Study plans</Link> → <Link href={planHref}>{plan.title}</Link>
      </nav>
      <h1 className={v.title}>{title}</h1>
      {project && item.description && (
        <>
          <button type="button" className={v.linkButton} aria-expanded={showProject} onClick={() => setShowProject((o) => !o)}>
            Project description <ChevronDown size={14} aria-hidden className={cn(showProject && v.flip)} />
          </button>
          {showProject && <div className={cn(v.card, v.prose)}><Markdown>{item.description}</Markdown></div>}
        </>
      )}

      <section className={v.card} aria-label="Stage">
        <div className={v.cardHead}>
          <h2>{project ? stage.title ?? `Stage ${stage.stageNo}` : "Task"}</h2>
          {stage.difficulty && <DifficultyBadge value={stage.difficulty} />}
          {stage.status === "DONE" && <span className={v.done}><Check size={15} aria-hidden /> Done</span>}
        </div>
        {!stage.published || !stage.slug ? (
          <p className={v.muted}>This stage is being updated. Try again later.</p>
        ) : stage.status === "LOCKED" ? (
          <p className={v.locked}><Lock size={16} aria-hidden /> Finish stage {stage.stageNo - 1} first.</p>
        ) : (
          <CodeExercise problemSlug={stage.slug} passed={stage.status === "DONE"} onSolved={reload}
            multiFile={(p) => (
              <div className={v.ide}>
                <h3>Write the project in IntelliJ IDEA</h3>
                <p className={v.muted}>This stage has several files. Open it in IntelliJ, code, then use <b>Check Stage</b> in the IDE.</p>
                <IdeButton planSlug={plan.slug} itemId={item.id} stageNo={stage.stageNo} />
                <Link href={`/problems/${p.slug}`} className={v.linkButton}>Prefer the browser? Solve it in the web workspace</Link>
              </div>
            )} />
        )}
      </section>

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
