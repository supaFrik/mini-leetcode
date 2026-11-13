"use client";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { ArrowRight, Check, ChevronRight, Clock, ListChecks } from "lucide-react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { setTopicSkipped } from "@/lib/services";
import type { StudyPlanStage, TopicRef } from "@/types/api";
import t from "./topics.module.css";

/** Chủ đề kế tiếp nên học: chủ đề đầu tiên chưa xong và chưa Skip. */
export function nextTopic(topics: TopicRef[]): TopicRef | undefined {
  return topics.find((x) => x.progress !== "DONE" && x.progress !== "SKIPPED");
}

export function topicHref(slug: string, planSlug: string, stageNo: number) {
  return `/topics/${slug}?plan=${encodeURIComponent(planSlug)}&stage=${stageNo}`;
}

/** "Mini Bank 2/5 — Kế thừa & đa hình" ⇒ "Kế thừa & đa hình" (tiêu đề đề thường lặp lại số stage). */
function stageName(stage: StudyPlanStage) {
  const title = stage.title ?? `Problem #${stage.problemId}`;
  return title.replace(/^.*?\d+\s*\/\s*\d+\s*[—–-]\s*/, "") || title;
}

function Dot({ topic, current }: { topic: TopicRef; current: boolean }) {
  if (topic.progress === "DONE") {
    return <span className={cn(t.dot, t.dotDone)} aria-label="Done"><Check size={12} strokeWidth={3} /></span>;
  }
  return (
    <span
      className={cn(t.dot, current && t.dotCurrent, topic.progress === "SKIPPED" && t.dotSkipped,
        topic.progress === "IN_PROGRESS" && !current && t.dotProgress)}
      aria-label={topic.progress === "SKIPPED" ? "Skipped" : topic.progress === "IN_PROGRESS" ? "In progress" : "Not started"}
    />
  );
}

/**
 * Một stage của project kiểu Hyperskill: dòng thời gian các chủ đề (Continue / Skip ở chủ đề kế tiếp) rồi tới
 * chính stage (bài code lớn). Chủ đề không khoá stage (specs/topics.spec.md, T1).
 */
export function StageTimeline({
  stage, stageCount, planSlug, defaultOpen, stageTask, onChanged,
}: {
  stage: StudyPlanStage; stageCount: number; planSlug: string; defaultOpen: boolean;
  /** Dòng bài code của stage (link tới workspace hoặc trạng thái khoá). */
  stageTask: ReactNode;
  onChanged: () => void;
}) {
  const topics = stage.topics ?? [];
  const [open, setOpen] = useState(defaultOpen);
  const [busy, setBusy] = useState<string | null>(null);
  const current = nextTopic(topics);
  const finished = topics.filter((x) => x.progress === "DONE").length;

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
        <span>Stage {stage.stageNo}/{stageCount}: {stageName(stage)}</span>
        <span className={t.stageMeta}>{finished}/{topics.length} topics{stage.status === "DONE" ? " · stage done" : ""}</span>
      </button>
      {open && (
        <ol className={t.timeline} aria-label={`Stage ${stage.stageNo} topics`}>
          {topics.map((topic) => {
            const isCurrent = topic === current;
            const href = topicHref(topic.slug, planSlug, stage.stageNo);
            return (
              <li key={topic.id} className={cn(t.node, topic.progress === "SKIPPED" && t.nodeSkipped)}>
                <Dot topic={topic} current={isCurrent} />
                <Link href={href} className={t.nodeTitle}>{topic.title}</Link>
                {topic.progress === "SKIPPED" && (
                  <>
                    {" "}<span className={t.tag}>Skipped</span>{" "}
                    <button type="button" className={t.linkButton} disabled={busy === topic.slug}
                      onClick={() => toggleSkip(topic, false)}>Undo</button>
                  </>
                )}
                {isCurrent && (
                  <>
                    {topic.summary && <p className={t.nodeSummary}>{topic.summary}</p>}
                    <div className={t.nodeMeta}>
                      <span><Clock size={14} aria-hidden /> {topic.estMinutes} min</span>
                      <span><ListChecks size={14} aria-hidden /> {topic.doneSteps}/{topic.stepCount} steps</span>
                    </div>
                    <div className={t.nodeActions}>
                      <Link href={href} className={t.primary}>
                        {topic.progress === "IN_PROGRESS" ? "Continue" : "Start"} <ArrowRight size={15} aria-hidden />
                      </Link>
                      <button type="button" className={t.secondary} disabled={busy === topic.slug}
                        onClick={() => toggleSkip(topic, true)}>Skip</button>
                    </div>
                  </>
                )}
              </li>
            );
          })}
          <li className={t.node}>
            <span className={cn(t.dot, t.dotStage, stage.status === "DONE" && t.dotDone, !current && stage.status !== "DONE" && t.dotCurrent)}
              aria-hidden>{stage.status === "DONE" && <Check size={12} strokeWidth={3} />}</span>
            {stageTask}
          </li>
        </ol>
      )}
    </div>
  );
}
