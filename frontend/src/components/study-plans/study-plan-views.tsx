"use client";
import Link from "next/link";
import { useState } from "react";
import { Check, CircleDot, Circle, Lock, Wrench } from "lucide-react";
import { cn } from "@/lib/utils";
import { useLoad } from "@/hooks/use-load";
import { fetchStudyPlan, fetchStudyPlans } from "@/lib/services";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./study-plans.module.css";
import type { StudyPlanItem, StudyPlanStage } from "@/types/api";

function Progress({ done, total }: { done: number; total: number }) {
  const pct = total > 0 ? Math.round((done * 100) / total) : 0;
  return (
    <div className={s.progress}>
      <span className={s.bar} role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Progress">
        <i style={{ width: `${pct}%` }} />
      </span>
      <span>{done}/{total} stages · {pct}%</span>
    </div>
  );
}

function LoadError({ message, onRetry, what }: { message: string; onRetry: () => void; what: string }) {
  return (
    <div className={cn(p.table, p.empty, p.error)} role="alert">
      <h2>Couldn&apos;t load {what}</h2>
      <p>{message}</p>
      <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={onRetry}>Try again</button>
    </div>
  );
}

/** /study-plans — các plan PUBLISHED (staff thấy cả bản nháp) với tiến độ của người xem. */
export function StudyPlanList() {
  const [attempt, setAttempt] = useState(0);
  const load = useLoad(fetchStudyPlans, [attempt]);

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>Study plans</h1>
        <p className={s.lead}>
          Learning paths built from multi-stage projects and short practice problems. Each project stage extends the code you
          wrote in the previous one — finish a stage to unlock the next.
        </p>

        {load.state === "loading" && <div className={cn(p.table, p.empty)} aria-busy="true"><p>Loading study plans…</p></div>}
        {load.state === "error" && <LoadError what="study plans" message={load.message} onRetry={() => setAttempt((n) => n + 1)} />}
        {load.state === "ok" && load.data.length === 0 && (
          <div className={cn(p.table, p.empty)}>
            <h2>No study plans yet</h2>
            <p>Your instructors haven&apos;t published a plan. Practice on the problem list meanwhile.</p>
          </div>
        )}
        {load.state === "ok" && load.data.length > 0 && (
          <div className={s.grid}>
            {load.data.map((plan) => (
              <Link key={plan.id} href={`/study-plans/${plan.slug}`} className={s.card}>
                {plan.status !== "PUBLISHED" && <span className={s.badge}>{plan.status}</span>}
                <h2>{plan.title}</h2>
                {plan.description && <p>{plan.description}</p>}
                <span className={s.meta}>{plan.itemCount} items · {plan.stageCount} stages</span>
                <Progress done={plan.doneStages} total={plan.stageCount} />
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function StageIcon({ stage }: { stage: StudyPlanStage }) {
  if (!stage.published) return <Wrench size={18} className={s.locked} aria-label="Under maintenance" />;
  switch (stage.status) {
    case "DONE": return <Check size={18} strokeWidth={2.4} className={s.iconDone} aria-label="Done" />;
    case "IN_PROGRESS": return <CircleDot size={18} className={s.iconProgress} aria-label="In progress" />;
    case "LOCKED": return <Lock size={16} className={s.locked} aria-label="Locked" />;
    default: return <Circle size={18} className={s.locked} aria-label="Not started" />;
  }
}

function StageRow({ item, stage }: { item: StudyPlanItem; stage: StudyPlanStage }) {
  const label = item.kind === "PROJECT" ? `Stage ${stage.stageNo}` : "Practice";
  const title = stage.title ?? `Problem #${stage.problemId}`;
  const content = (
    <>
      <StageIcon stage={stage} />
      <span className={s.stageNo}>{label}</span>
      <span className={cn(stage.status === "LOCKED" && s.locked)}>{title}</span>
      <span>
        {!stage.published ? (
          <span className={s.hint}>Being updated — try later</span>
        ) : stage.status === "LOCKED" ? (
          <span className={s.hint}>Finish stage {stage.stageNo - 1} first</span>
        ) : (
          stage.difficulty && <DifficultyBadge value={stage.difficulty} />
        )}
      </span>
    </>
  );
  const open = stage.published && stage.status !== "LOCKED" && stage.slug;
  return open ? (
    <Link href={`/problems/${stage.slug}`} className={s.stage}>{content}</Link>
  ) : (
    <div className={s.stage} aria-disabled="true">{content}</div>
  );
}

/** /study-plans/[slug] — mục theo thứ tự, stage mở khoá tuần tự trong mỗi project. */
export function StudyPlanDetailView({ slug }: { slug: string }) {
  const [attempt, setAttempt] = useState(0);
  const load = useLoad(() => fetchStudyPlan(slug), [slug, attempt]);

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <Link href="/study-plans" className={s.back}>← Study plans</Link>
        {load.state === "loading" && <div className={cn(p.table, p.empty)} aria-busy="true"><p>Loading study plan…</p></div>}
        {load.state === "error" && <LoadError what="this study plan" message={load.message} onRetry={() => setAttempt((n) => n + 1)} />}
        {load.state === "ok" && (
          <>
            <div className={s.headerBox}>
              <h1 className={s.title}>{load.data.title}</h1>
              {load.data.description && <p className={s.lead} style={{ margin: 0 }}>{load.data.description}</p>}
              <Progress done={load.data.doneStages} total={load.data.stageCount} />
            </div>

            <div className={s.ide}>
              <span>Prefer your IDE? Open stages and check them from IntelliJ IDEA with the Mini LeetCode plugin.</span>
              <Link href="/ide">Set up the plugin →</Link>
            </div>

            <div className={s.items}>
              {load.data.items.map((item) => (
                <section key={item.id} className={s.item} aria-label={item.title}>
                  <div className={s.itemHead}>
                    <span className={s.kind}>{item.kind === "PROJECT" ? "Project" : "Practice"}</span>
                    <h2>{item.title}</h2>
                    {item.done && <span className={s.done}>Completed</span>}
                    {item.description && <p>{item.description}</p>}
                  </div>
                  {item.stages.map((stage) => <StageRow key={stage.stageNo} item={item} stage={stage} />)}
                </section>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
