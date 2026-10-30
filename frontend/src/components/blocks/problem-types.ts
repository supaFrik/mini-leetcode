export type ProblemDifficulty = "EASY" | "MEDIUM" | "HARD";

export type ProblemVerdict =
  | "AC"
  | "WA"
  | "TLE"
  | "MLE"
  | "CE"
  | "RE"
  | "SV"
  | "FLAGGED_CHEATING";

/** Per-testcase verdicts reported by the judge. */
export type ProblemCaseVerdict = "AC" | "WA" | "TLE" | "MLE" | "RE" | "SKIPPED";

/**
 * The problem model the block renders.
 *
 * This file intentionally declares its own types instead of importing from the
 * host application's `@/types/api`. Keeping the block self-contained is what
 * makes `shadcn add problem-detail` work in any project: the host passes its
 * own domain models, and TypeScript's structural typing accepts them as long
 * as the shapes line up.
 */
export interface ProblemDetailProblem {
  id: number;
  title: string;
  /** Markdown source. */
  description: string;
  difficulty: ProblemDifficulty;
  timeLimitMs?: number;
  memoryLimitMb?: number;
  /** Lifecycle marker such as DRAFT / REVIEWING / PUBLISHED / ARCHIVED. */
  status?: string;
  likes?: number | null;
  dislikes?: number | null;
}

export interface ProblemDetailTestCase {
  id: number;
  input: string;
  expectedOutput: string;
  isHidden: boolean;
  explanation?: string;
}

export interface ProblemDetailCaseResult {
  index: number;
  verdict: ProblemCaseVerdict;
  hidden: boolean;
  input?: string;
  expected?: string;
  actual?: string;
  timeMs?: number;
}

export interface ProblemDetailSubmission {
  status?: string;
  verdict?: ProblemVerdict | null;
  executionTimeMs?: number | null;
  memoryUsedKb?: number | null;
  passTestCases?: number;
  totalTestCases?: number;
  errorMessage?: string | null;
  memoryPercentile?: number | null;
}

export interface ProblemVerdictMeta {
  label: string;
  ok: boolean;
}

export const PROBLEM_VERDICT_META: Record<ProblemVerdict, ProblemVerdictMeta> = {
  AC: { label: "Accepted", ok: true },
  WA: { label: "Wrong Answer", ok: false },
  TLE: { label: "Time Limit Exceeded", ok: false },
  MLE: { label: "Memory Limit Exceeded", ok: false },
  CE: { label: "Compilation Error", ok: false },
  RE: { label: "Runtime Error", ok: false },
  SV: { label: "Security Violation", ok: false },
  FLAGGED_CHEATING: { label: "Flagged (Cheating)", ok: false },
};

export interface ProblemDifficultyMeta {
  label: string;
  className: string;
}

export const PROBLEM_DIFFICULTY_META: Record<
  ProblemDifficulty,
  ProblemDifficultyMeta
> = {
  EASY: {
    label: "Easy",
    className:
      "border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  },
  MEDIUM: {
    label: "Medium",
    className:
      "border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400",
  },
  HARD: {
    label: "Hard",
    className:
      "border-red-500/30 bg-red-500/10 text-red-600 dark:text-red-400",
  },
};

/** Formats a kilobyte reading the way the judge reports it. */
export function formatProblemMemory(kb: number | null | undefined): string {
  if (kb == null) return "—";
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}
