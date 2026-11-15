import type { ProblemLifecycle, StudyPlanStatus } from "@/types/api";

type Tone = "default" | "secondary" | "success" | "warning" | "outline";

/** Màu badge trạng thái của study plan và chủ đề (cùng enum StudyPlanStatus). */
export const PLAN_STATUS_VARIANT: Record<StudyPlanStatus, Tone> = {
  PUBLISHED: "success", DRAFT: "secondary", ARCHIVED: "outline",
};

/** Màu badge vòng đời đề. */
export const PROBLEM_STATUS_TONE: Record<ProblemLifecycle, Tone> = {
  DRAFT: "secondary", REVIEWING: "warning", PUBLISHED: "success", ARCHIVED: "outline",
};
