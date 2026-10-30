"use client";

import Link from "next/link";
import { ProblemResult } from "@/components/blocks/problem-result";
import { USE_MOCK } from "@/lib/api";
import type { CaseResult, Submission } from "@/types/api";

export interface ResultPanelProps {
  result: Submission | null;
  cases: CaseResult[] | null;
  judging: boolean;
  timedOut: boolean;
  error: { message: string } | null;
  runMode?: boolean;
}

/**
 * Judge verdict panel for the workspace and submission detail pages.
 *
 * Thin adapter over the `problem-result` registry block that maps the
 * application's `Submission` / `CaseResult` models onto the block's props.
 */
export function ResultPanel({
  result,
  cases,
  judging,
  timedOut,
  error,
  runMode,
}: ResultPanelProps) {
  return (
    <>
      <ProblemResult
        result={result}
        cases={cases}
        judging={judging}
        timedOut={timedOut}
        error={error}
        runMode={runMode}
        memoryLabel="Peak memory"
        timedOutHint={
          <>
            Judging is taking longer than expected. Your submission has been
            saved; view results in{" "}
            <Link href="/submissions" className="underline">
              Submissions
            </Link>
            .
          </>
        }
        emptyHint={
          <span className="block space-y-2">
            <span className="block">Run code or submit solution to see results.</span>
            {USE_MOCK ? (
              <span className="block text-xs">
                Mock Judge: default template is WA, while(true) is TLE, throw new is
                RE, syntax error is CE, else AC.
              </span>
            ) : null}
          </span>
        }
      />
    </>
  );
}
