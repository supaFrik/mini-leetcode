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
      <p className="eyebrow mb-1.5">{label}</p>
      <pre className="overflow-auto whitespace-pre-wrap rounded-sm border border-border bg-card px-3 py-2 font-mono text-code text-ink">
        {children}
      </pre>
    </div>
  );
}

const CASE_VERDICT_CLASS: Record<ProblemCaseVerdict, string> = {
  AC: "bg-success",
  SKIPPED: "bg-faint",
  WA: "bg-destructive",
  TLE: "bg-destructive",
  MLE: "bg-destructive",
  RE: "bg-destructive",
};

/**
 * Judge verdict summary with a per-testcase breakdown.
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
    const firstFailing = cases.findIndex((c) => c.verdict !== "AC" && c.verdict !== "SKIPPED");
    setSelected(Math.max(0, firstFailing));
  }, [cases]);

  if (error) {
    return (
      <div
        role="alert"
        className="m-4 rounded-sm border border-destructive/30 bg-destructive/5 p-3 text-body-md text-destructive"
      >
        {error.message}
      </div>
    );
  }

  if (judging) {
    return (
      <div className="flex items-center gap-2 p-4 text-body-md text-body" aria-live="polite">
        <Spinner />
        {result?.status === "JUDGING"
          ? "Evaluating solution…"
          : "Submission received, waiting in judge queue…"}
      </div>
    );
  }

  if (timedOut) {
    return (
      <p className="p-4 text-body-md text-[#8a5a00]" role="status">
        {timedOutHint}
      </p>
    );
  }

  if (!result?.verdict) {
    return <p className="p-4 text-body-md text-mute">{emptyHint}</p>;
  }

  const verdict = PROBLEM_VERDICT_META[result.verdict];
  const current = cases?.[selected];

  return (
    <div className="space-y-4 p-4">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 rounded-lg border border-border bg-hairline-soft px-4 py-3">
        <span
          className={cn(
            "flex items-center gap-2 text-heading-md",
            verdict.ok ? "text-success" : "text-destructive"
          )}
        >
          <span
            className={cn(
              "grid h-5 w-5 place-items-center rounded-full text-white",
              verdict.ok ? "bg-success" : "bg-destructive"
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

        <span className="font-mono text-body-sm text-body">
          Runtime: {result.executionTimeMs ?? "—"} ms
          <span className="mx-2 text-faint">|</span> {memoryLabel}:{" "}
          {formatProblemMemory(result.memoryUsedKb)}
        </span>

        {runMode ? (
          <Badge variant="outline" className="ml-auto">
            Run: public testcases only
          </Badge>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg border border-border bg-card p-3">
          <p className="eyebrow">Status</p>
          <p className={cn("mt-1 font-medium", verdict.ok ? "text-success" : "text-destructive")}>
            {verdict.ok ? "Passed" : "Failed"} ({result.passTestCases ?? 0}/{result.totalTestCases ?? 0})
          </p>
        </div>

        {result.memoryPercentile != null ? (
          <div className="rounded-lg border border-border bg-card p-3">
            <p className="eyebrow">Memory percentile</p>
            <p className="mt-1 font-medium text-ink">Beats {result.memoryPercentile} %</p>
          </div>
        ) : null}
      </div>

      {result.errorMessage ? (
        <pre className="max-h-40 overflow-auto rounded-sm border border-destructive/25 bg-destructive/5 p-3 font-mono text-code text-destructive">
          {result.errorMessage}
        </pre>
      ) : null}

      {cases && cases.length > 0 ? (
        <>
          <Tabs value={String(selected)} onValueChange={(value) => setSelected(Number(value))}>
            <TabsList className="h-auto w-full flex-wrap justify-start gap-1 border-b-0 p-0">
              {cases.map((c, i) => (
                <TabsTrigger
                  key={c.index ?? i}
                  value={String(i)}
                  className="gap-1.5 rounded-sm border border-border px-2.5 py-1 text-body-sm after:hidden data-[state=active]:border-ink data-[state=active]:bg-hairline-soft"
                >
                  <span className={cn("h-1.5 w-1.5 rounded-full", CASE_VERDICT_CLASS[c.verdict])} aria-hidden="true" />
                  Case {i + 1}
                  {c.hidden ? <span className="text-mute">(hidden)</span> : null}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {current ? (
            <div className="space-y-3 rounded-lg border border-border bg-card p-4">
              {current.hidden ? (
                <p className="text-body-md text-mute">
                  Hidden testcase: only verdict is shown (
                  {current.verdict === "SKIPPED"
                    ? "skipped"
                    : PROBLEM_VERDICT_META[current.verdict as Exclude<ProblemCaseVerdict, "SKIPPED">]?.label ??
                      current.verdict}
                  ), data is private.
                </p>
              ) : current.verdict === "SKIPPED" ? (
                <p className="text-body-md text-mute">
                  Skipped: testing stopped on the first failing test case (Fail-Fast).
                </p>
              ) : current.verdict === "TLE" || current.verdict === "MLE" || current.verdict === "RE" ? (
                <p className="text-body-md text-destructive">
                  {PROBLEM_VERDICT_META[current.verdict].label} on this testcase.
                </p>
              ) : (
                <>
                  <CodeBox label="Input">{current.input}</CodeBox>
                  <ProblemDiffView expected={current.expected ?? ""} actual={current.actual ?? ""} />
                </>
              )}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
