"use client";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { ArrowRight, Check, ChevronRight, Circle, CircleDot, Clock, ListChecks, Lock, SkipForward } from "lucide-react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { setTopicSkipped } from "@/lib/services";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { StageIcon } from "@/components/study-plans/stage-status-icon";
import type { StudyPlanStage, TopicRef } from "@/types/api";
import t from "./topics.module.css";

/** Chủ đề kế tiếp nên học: chủ đề đầu tiên chưa xong và chưa Skip. */
export function nextTopic(topics: TopicRef[]): TopicRef | undefined {
  return topics.find((x) => x.progress !== "DONE" && x.progress !== "SKIPPED");
}

export function topicHref(slug: string, planSlug: string, stageNo: number) {
  return `/topics/${slug}?plan=${encodeURIComponent(planSlug)}&stage=${stageNo}`;
}

/** "Mini Bank 2/5 — Kế thừa & đa hình" ⇒ "Kế thừa & đa hình" (tiêu đề đề thường lặp lại số stage; bỏ mọi dấu ngăn sau "N/M"). */
// RegExp constructor: tsconfig target does not allow the `u` flag in a regex literal.
// Matches from the last digit before "/" (no lazy ".*?" + "\d+" overlap), so the search is linear.
const STAGE_NUMBER = new RegExp("\\d\\s*/\\s*\\d+[^\\p{L}\\p{N}]*", "u");

export function stageName(stage: Pick<StudyPlanStage, "title" | "problemId">) {
  const title = stage.title ?? `Problem #${stage.problemId}`;
  const m = STAGE_NUMBER.exec(title);
  // Prefix must sit on the first line, as the old "^.*?" required.
  const ok = m && /^.*$/.test(title.slice(0, m.index));
  return (ok ? title.slice(m.index + m[0].length) : title) || title;
}

// SKIPPED đã có nhãn "Skipped" + Undo cạnh tên chủ đề.
const PROGRESS_LABEL: Record<Exclude<TopicRef["progress"], "SKIPPED">, string> = {
  NEW: "Not started", IN_PROGRESS: "In progress", DONE: "Done",
};

/** Icon trạng thái nhỏ của một chủ đề. */
function TopicIcon({ progress }: { progress: TopicRef["progress"] }) {
  if (progress === "DONE") return <Check size={15} aria-hidden className={cn(t.topicIcon, t.topicIconDone)} />;
  if (progress === "IN_PROGRESS") return <CircleDot size={15} aria-hidden className={cn(t.topicIcon, t.topicIconProgress)} />;
  if (progress === "SKIPPED") return <SkipForward size={15} aria-hidden className={t.topicIcon} />;
  return <Circle size={15} aria-hidden className={t.topicIcon} />;
}

/**
 * Một stage của project kiểu Hyperskill, hai khối: ① các chủ đề lý thuyết (mỗi chủ đề có nút; nút chính và Skip
 * chỉ ở chủ đề đang học) và ② bài code của stage. Stage khoá chỉ là một dòng mờ. Chủ đề không khoá stage
 * (specs/topics.spec.md, T1).
 */
export function StageTimeline({
  stage, stageCount, planSlug, defaultOpen, stageTask, onChanged,
}: {
  stage: StudyPlanStage; stageCount: number; planSlug: string; defaultOpen: boolean;
  /** Khối bài code của stage. */
  stageTask: ReactNode;
  onChanged: () => void;
}) {
  const topics = stage.topics ?? [];
  const [open, setOpen] = useState(defaultOpen);
  const [busy, setBusy] = useState<string | null>(null);
  const current = nextTopic(topics);
  // Hai bước của stage: 1 = học chủ đề, 2 = bài code (chủ đề xong/Skip hết hoặc stage đã xong).
  const step = stage.status === "DONE" || !current ? 2 : 1;
  const name = `Stage ${stage.stageNo}/${stageCount} · ${stageName(stage)}`;

  if (stage.status === "LOCKED") {
    return (
      <div className={cn(t.stageBlock, t.stageLocked)}>
        <Lock size={16} aria-hidden />
        <span>{name}</span>
        <span className={t.stageMeta}>Finish stage {stage.stageNo - 1} to unlock</span>
        {stage.difficulty && <DifficultyBadge value={stage.difficulty} />}
      </div>
    );
  }

  const toggleSkip = async (topic: TopicRef, skipped: boolean) => {
    setBusy(topic.slug);
    try {
      await setTopicSkipped(topic.slug, skipped);
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className={t.stageBlock}>
      <button type="button" className={t.stageToggle} aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <ChevronRight size={16} className={cn(t.chevron, open && t.chevronOpen)} aria-hidden />
        <StageIcon stage={stage} />
        <span>{name}</span>
        <span className={t.stageMeta}>Step {step}/2</span>
        {stage.difficulty && <DifficultyBadge value={stage.difficulty} />}
      </button>
      {open && (
        <div className={t.stageBody}>
          <section className={t.column} aria-label="Learn the theory">
            <h3 className={t.columnTitle}>① Learn the theory</h3>
            <ol className={t.topicList} aria-label={`Stage ${stage.stageNo} topics`}>
              {topics.map((topic) => {
                const isCurrent = topic === current;
                const href = topicHref(topic.slug, planSlug, stage.stageNo);
                const action = topic.progress === "DONE" ? "Review" : topic.progress === "IN_PROGRESS" ? "Continue" : "Start";
                return (
                  <li key={topic.id} className={cn(t.topicRow, topic.progress === "SKIPPED" && t.nodeSkipped)}>
                    <TopicIcon progress={topic.progress} />
                    <div className={t.topicMain}>
                      <Link href={href} className={cn(t.nodeTitle, isCurrent && t.topicCurrent)}>{topic.title}</Link>
                      {topic.progress === "SKIPPED" && (
                        <>
                          {" "}<span className={t.tag}>Skipped</span>{" "}
                          <button type="button" className={t.linkButton} disabled={busy === topic.slug}
                            onClick={() => toggleSkip(topic, false)}>Undo</button>
                        </>
                      )}
                      <div className={t.topicMeta}>
                        {topic.progress !== "SKIPPED" && <span>{PROGRESS_LABEL[topic.progress]}</span>}
                        <span><ListChecks size={13} aria-hidden /> {topic.doneSteps}/{topic.stepCount} steps</span>
                        <span><Clock size={13} aria-hidden /> {topic.estMinutes} min</span>
                      </div>
                      {isCurrent && topic.summary && <p className={t.nodeSummary}>{topic.summary}</p>}
                    </div>
                    <div className={t.topicActions}>
                      {isCurrent && (
                        <button type="button" className={t.linkButton} disabled={busy === topic.slug}
                          onClick={() => toggleSkip(topic, true)}>Skip</button>
                      )}
                      <Link href={href} className={cn(isCurrent ? t.primary : t.secondary, t.small)}>
                        {action}{isCurrent && <ArrowRight size={14} aria-hidden />}
                      </Link>
                    </div>
                  </li>
                );
              })}
            </ol>
          </section>
          <section className={t.column} aria-label="Write the code">
            <h3 className={t.columnTitle}>② Write the code</h3>
            {stageTask}
          </section>
        </div>
      )}
    </div>
  );
}
