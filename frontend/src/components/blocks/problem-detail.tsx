"use client";

import { cn } from "@/lib/utils";
import { ProblemHeader } from "./problem-header";
import { ProblemDescription } from "./problem-description";
import { ProblemTestCases } from "./problem-test-cases";
import { ProblemResult } from "./problem-result";
import type {
  ProblemDetailCaseResult,
  ProblemDetailProblem,
  ProblemDetailSubmission,
  ProblemDetailTestCase,
} from "./problem-types";

export interface ProblemDetailEditor {
  title: string;
  onTitleChange: (value: string) => void;
  description: string;
  onDescriptionChange: (value: string) => void;
  /** Whether the description is currently shown as an editable field. */
  editing: boolean;
  onStartEdit: () => void;
  onSave: () => void;
  onCancel: () => void;
  busy?: boolean;
  error?: string | null;
}

export interface ProblemDetailProps {
  problem: ProblemDetailProblem;
  cases: ProblemDetailTestCase[];
  /** Latest judge result for the verdict panel; pass null to show the empty state. */
  result?: ProblemDetailSubmission | null;
  /** Per-case judge breakdown, if available. */
  caseResults?: ProblemDetailCaseResult[] | null;
  judging?: boolean;
  timedOut?: boolean;
  error?: { message: string } | null;
  runMode?: boolean;
  /** Hide hidden testcases from the examples list. */
  showHiddenCases?: boolean;
  /** Render the public testcases section. */
  showTestCases?: boolean;
  /** Render the judge verdict panel. */
  showResult?: boolean;
  /** When provided, the title and description become editable. */
  editor?: ProblemDetailEditor;
  /** Extra controls rendered in the header, e.g. a watchlist toggle. */
  actions?: React.ReactNode;
  className?: string;
}

/**
 * Problem Detail block.
 *
 * Data-driven composition of the judge problem view: header (id, title,
 * difficulty, lifecycle), markdown description with time/memory limits, public
 * test cases, and a verdict result panel with per-case diffs.
 *
 * Drop-in replacement for the description column of the practice workspace:
 *
 * ```tsx
 * <ProblemDetail
 *   problem={problem}
 *   cases={cases}
 *   result={judge.result}
 *   caseResults={judge.cases}
 *   judging={judge.judging}
 *   timedOut={judge.timedOut}
 *   error={judge.error}
 * />
 * ```
 */
export function ProblemDetail({
  problem,
  cases,
  result = null,
  caseResults = null,
  judging = false,
  timedOut = false,
  error = null,
  runMode = false,
  showHiddenCases = true,
  showTestCases = true,
  showResult = true,
  editor,
  actions,
  className,
}: ProblemDetailProps) {
  return (
    <div className={cn("h-full overflow-auto", className)}>
      <div className="sticky top-0 z-10 border-b bg-card px-3 pt-2">
        <span className="inline-flex items-center gap-1.5 rounded-t-sm bg-hairline-soft px-3 py-1.5 text-body-md text-ink">
          Description
        </span>
      </div>

      <div className="space-y-5 p-6">
        <ProblemHeader
          id={problem.id}
          title={problem.title}
          difficulty={problem.difficulty}
          status={problem.status}
          likes={problem.likes}
          dislikes={problem.dislikes}
          actions={actions}
          editor={
            editor
              ? {
                  title: editor.title,
                  onTitleChange: editor.onTitleChange,
                  editing: editor.editing,
                  onStartEdit: editor.onStartEdit,
                }
              : undefined
          }
        />

        <ProblemDescription
          markdown={problem.description}
          timeLimitMs={problem.timeLimitMs}
          memoryLimitMb={problem.memoryLimitMb}
          editor={
            editor
              ? {
                  value: editor.description,
                  onChange: editor.onDescriptionChange,
                  editing: editor.editing,
                  onSave: editor.onSave,
                  onCancel: editor.onCancel,
                  busy: editor.busy,
                  error: editor.error,
                }
              : undefined
          }
        />

        {showTestCases ? (
          <ProblemTestCases cases={cases} showHidden={showHiddenCases} />
        ) : null}

        {showResult ? (
          <section aria-label="Judge result" className="rounded-lg border">
            <ProblemResult
              result={result}
              cases={caseResults}
              judging={judging}
              timedOut={timedOut}
              error={error}
              runMode={runMode}
            />
          </section>
        ) : null}
      </div>
    </div>
  );
}

export { ProblemHeader } from "./problem-header";
export { ProblemDescription } from "./problem-description";
export { ProblemTestCases } from "./problem-test-cases";
export { ProblemResult } from "./problem-result";
export { ProblemDiffView } from "./problem-diff-view";
export * from "./problem-types";
