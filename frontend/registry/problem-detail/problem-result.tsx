"use client";

import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { ProblemDiffView } from "./problem-diff-view";
import {
  PROBLEM_VERDICT_META,
  formatProblemMemory,
  type ProblemCaseVerdict,
  type ProblemDetailCaseResult,
  type ProblemDetailSubmission,
} from "./problem-types";

export interface ProblemResultProps {
  result: ProblemDetailSubmission | null;
  cases?: ProblemDetailCaseResult[] | null;
  judging?: boolean;
  timedOut?: boolean;
  error?: { message: string } | null;
  runMode?: boolean;
  emptyHint?: React.ReactNode;
  /** Message shown when judging exceeded the polling window. */
  timedOutHint?: React.ReactNode;
  /** Label for the memory figure in the verdict banner. */
  memoryLabel?: string;
}

function Spinner() {
  return (
    <span
      className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
      aria-hidden="true"
    />
  );
}

function CodeBox({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="mb-1 font-mono text-[11px] uppercase text-muted-foreground">
        {label}
      </p>
      <pre className="overflow-auto whitespace-pre-wrap rounded-md border bg-black/90 px-3 py-2 font-mono text-xs text-white">
        {children}
      </pre>
    </div>
  );
}

const CASE_VERDICT_CLASS: Record<ProblemCaseVerdict, string> = {
  AC: "bg-primary",
  SKIPPED: "bg-muted-foreground",
  WA: "bg-destructive",
  TLE: "bg-destructive",
  MLE: "bg-destructive",
  RE: "bg-destructive",
};

/**
 * Judge verdict summary with a per-testcase breakdown.
 *
 * The verdict banner, status tiles and testcase selector are built from
 * shadcn `Badge` and `Tabs` primitives so the block matches the host theme.
 */
export function ProblemResult({
  result,
  cases,
  judging,
  timedOut,
  error,
  runMode,
  emptyHint = "Run code or submit solution to see results.",
  timedOutHint = "Judging took longer than expected. Your submission has been saved; check the Submissions tab.",
  memoryLabel = "Memory",
}: ProblemResultProps) {
  const [selected, setSelected] = useState(0);

  // Jump to the first failing case whenever a new breakdown arrives.
  useEffect(() => {
    if (!cases) return;
    const firstFailing = cases.findIndex(
      (c) => c.verdict !== "AC" && c.verdict !== "SKIPPED"
    );
    setSelected(Math.max(0, firstFailing));
  }, [cases]);

  if (error) {
    return (
      <div
        role="alert"
        className="m-4 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive"
      >
        {error.message}
      </div>
    );
  }

  if (judging) {
    return (
      <div
        className="flex items-center gap-2 p-4 text-sm text-muted-foreground"
        aria-live="polite"
      >
        <Spinner />
        {result?.status === "JUDGING"
          ? "Evaluating solution…"
          : "Submission received, waiting in judge queue…"}
      </div>
    );
  }

  if (timedOut) {
    return (
      <p className="p-4 text-sm text-amber-600 dark:text-amber-400" role="status">
        {timedOutHint}
      </p>
    );
  }

  if (!result?.verdict) {
    return <p className="p-4 text-sm text-muted-foreground">{emptyHint}</p>;
  }

  const verdict = PROBLEM_VERDICT_META[result.verdict];
  const current = cases?.[selected];

  return (
    <div className="space-y-3 p-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg bg-secondary px-4 py-3">
        <span
          className={cn(
            "flex items-center gap-2 text-lg font-semibold",
            verdict.ok ? "text-primary" : "text-destructive"
          )}
        >
          <span
            className={cn(
              "grid h-5 w-5 place-items-center rounded-full text-white",
              verdict.ok ? "bg-primary" : "bg-destructive"
            )}
          >
            {verdict.ok ? (
              <Check className="h-3 w-3" aria-hidden="true" />
            ) : (
              <X className="h-3 w-3" aria-hidden="true" />
            )}
          </span>
          {verdict.label}
        </span>

        <span className="font-mono text-xs text-muted-foreground">
          Runtime: {result.executionTimeMs ?? "—"} ms
          <span className="mx-2">|</span> {memoryLabel}:{" "}
          {formatProblemMemory(result.memoryUsedKb)}
        </span>

        {runMode ? (
          <Badge variant="outline" className="ml-auto font-normal">
            Run: public testcases only
          </Badge>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg bg-secondary p-3">
          <p className="text-xs text-muted-foreground">Status</p>
          <p
            className={cn(
              "font-semibold",
              verdict.ok ? "text-primary" : "text-destructive"
            )}
          >
            {verdict.ok ? "Passed" : "Failed"} ({result.passTestCases ?? 0}/
            {result.totalTestCases ?? 0})
          </p>
        </div>

        {result.memoryPercentile != null ? (
          <div className="rounded-lg bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Memory Percentile</p>
            <p className="font-semibold">Beats {result.memoryPercentile} %</p>
          </div>
        ) : null}
      </div>

      {result.errorMessage ? (
        <pre className="max-h-40 overflow-auto rounded-lg bg-black p-3 font-mono text-xs text-red-300">
          {result.errorMessage}
        </pre>
      ) : null}

      {cases && cases.length > 0 ? (
        <>
          <Tabs
            value={String(selected)}
            onValueChange={(value) => setSelected(Number(value))}
          >
            <TabsList className="h-auto w-full flex-wrap justify-start gap-1 bg-transparent p-0">
              {cases.map((c, i) => (
                <TabsTrigger
                  key={c.index ?? i}
                  value={String(i)}
                  className="gap-1.5 rounded-md border px-2.5 py-1 text-xs data-[state=active]:bg-accent"
                >
                  <span
                    className={cn(
                      "h-1.5 w-1.5 rounded-full",
                      CASE_VERDICT_CLASS[c.verdict]
                    )}
                    aria-hidden="true"
                  />
                  Case {i + 1}
                  {c.hidden ? (
                    <span className="text-muted-foreground">(hidden)</span>
                  ) : null}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {current ? (
            <div className="space-y-2 rounded-lg border p-3">
              {current.hidden ? (
                <p className="text-sm text-muted-foreground">
                  Hidden testcase: only verdict is shown (
                  {current.verdict === "SKIPPED"
                    ? "skipped"
                    : PROBLEM_VERDICT_META[
                        current.verdict as Exclude<ProblemCaseVerdict, "SKIPPED">
                      ]?.label ?? current.verdict}
                  ), data is private.
                </p>
              ) : current.verdict === "SKIPPED" ? (
                <p className="text-sm text-muted-foreground">
                  Skipped: testing stopped on the first failing test case (Fail-Fast).
                </p>
              ) : current.verdict === "TLE" ||
                current.verdict === "MLE" ||
                current.verdict === "RE" ? (
                <p className="text-sm text-destructive">
                  {PROBLEM_VERDICT_META[current.verdict].label} on this testcase.
                </p>
              ) : (
                <>
                  <CodeBox label="Input">{current.input}</CodeBox>
                  <ProblemDiffView
                    expected={current.expected ?? ""}
                    actual={current.actual ?? ""}
                  />
                </>
              )}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
