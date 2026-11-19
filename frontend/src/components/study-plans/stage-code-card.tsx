"use client";
import Link from "next/link";
import { ArrowRight, Check } from "lucide-react";
import { useLoad } from "@/hooks/use-load";
import { fetchProblem } from "@/lib/services";
import type { StudyPlanItem, StudyPlanStage } from "@/types/api";
import { IdeButton } from "./ide-button";
import s from "./study-plans.module.css";

/** Đoạn văn đầu tiên của đề Markdown, bỏ tiêu đề và ký hiệu định dạng, cắt ~180 ký tự. */
export function shortDescription(markdown: string, max = 180): string {
  // Blank-line split without \s (it overlaps \n): extra whitespace-only pieces are dropped by find().
  const para = markdown.split(/\n[^\S\n]*\n/).map((x) => x.trim()).find((x) => x && !/^(#|```|\||!\[|<)/.test(x)) ?? "";
  // Link classes exclude "[" so every scan stops at the next link: linear time.
  const text = para.replace(/[*_`>]/g, "").replace(/\[([^\][]*)\]\([^)[]*\)/g, "$1").replace(/\s+/g, " ").trim();
  if (text.length <= max) return text;
  // Whitespace is already single spaces here, so cutting at the last space drops the partial last word.
  const cut = text.slice(0, max);
  const lastSpace = cut.lastIndexOf(" ");
  return `${lastSpace >= 0 ? cut.slice(0, lastSpace) : cut}…`;
}

const STATUS_LABEL: Record<StudyPlanStage["status"], string> = {
  NEVER: "Not started", IN_PROGRESS: "Not passed yet", DONE: "Done", LOCKED: "Locked",
};

/** Khối "② Write the code" của một stage đang mở (stage khoá không mở được): mô tả ngắn của đề, mở trên web hoặc IntelliJ, trạng thái. */
export function StageCodeCard({ planSlug, item, stage }: { planSlug: string; item: StudyPlanItem; stage: StudyPlanStage }) {
  const open = stage.published && stage.status !== "LOCKED" && !!stage.slug;
  // Lỗi tải đề chỉ làm mất đoạn mô tả, nút vẫn dùng được.
  const problem = useLoad(() => (stage.slug ? fetchProblem(stage.slug) : Promise.reject(new Error("Stage has no problem slug"))),[stage.slug]);
  const summary = problem.state === "ok" ? shortDescription(problem.data.description) : "";

  return (
    <div className={s.codeCard}>
      {summary && <p className={s.codeSummary}>{summary}</p>}
      <p className={s.codeStatus}>
        {stage.status === "DONE" && <Check size={14} aria-hidden className={s.iconDone} />}
        {STATUS_LABEL[stage.status]}
      </p>
      {!stage.published ? (
        <p className={s.hint}>Being updated — try later</p>
      ) : open && (
        <div className={s.codeActions}>
          <Link href={`/study-plans/${planSlug}/${item.id}/${stage.stageNo}`} className={s.codeButton}>
            Open on the web <ArrowRight size={15} aria-hidden />
          </Link>
          <IdeButton planSlug={planSlug} itemId={item.id} stageNo={stage.stageNo} variant="link" />
        </div>
      )}
    </div>
  );
}
