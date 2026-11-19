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
  const para = markdown.split(/\n\s*\n/).map((x) => x.trim()).find((x) => x && !/^(#|```|\||!\[|<)/.test(x)) ?? "";
  const text = para.replace(/[*_`>]/g, "").replace(/\[([^\]]*)\]\([^)]*\)/g, "$1").replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max).replace(/\s+\S*$/, "")}…` : text;
}

const STATUS_LABEL: Record<StudyPlanStage["status"], string> = {
  NEVER: "Not started", IN_PROGRESS: "Not passed yet", DONE: "Done", LOCKED: "Locked",
};

/** Khối "② Write the code" của một stage đang mở (stage khoá không mở được): mô tả ngắn của đề, mở trên web hoặc IntelliJ, trạng thái. */
export function StageCodeCard({ planSlug, item, stage }: { planSlug: string; item: StudyPlanItem; stage: StudyPlanStage }) {
  const open = stage.published && stage.status !== "LOCKED" && !!stage.slug;
  // Lỗi tải đề chỉ làm mất đoạn mô tả, nút vẫn dùng được.
  const problem = useLoad(() => (stage.slug ? fetchProblem(stage.slug) : Promise.reject()), [stage.slug]);
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
