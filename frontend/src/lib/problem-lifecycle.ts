import type { ProblemLifecycle } from "@/types/api";

/** Cạnh hợp lệ của vòng đời đề — mirror đúng luật server (ProblemService.transition). */
export const PROBLEM_EDGES: Record<ProblemLifecycle, ProblemLifecycle[]> = {
  DRAFT: ["REVIEWING", "ARCHIVED"],
  REVIEWING: ["DRAFT", "PUBLISHED"],
  PUBLISHED: ["ARCHIVED"],
  ARCHIVED: ["DRAFT"],
};
