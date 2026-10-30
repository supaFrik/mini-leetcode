"use client";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ProblemDetailTestCase } from "./problem-types";

export interface ProblemTestCasesProps {
  cases: ProblemDetailTestCase[];
  /** When false, hidden testcases are omitted from the list. */
  showHidden?: boolean;
  className?: string;
}

/**
 * Public sample testcases rendered as shadcn `Card`-style panels.
 *
 * Hidden cases are flagged rather than hidden outright when `showHidden` is
 * on, so the sample list still communicates how many cases the judge runs.
 */
export function ProblemTestCases({
  cases,
  showHidden = true,
  className,
}: ProblemTestCasesProps) {
  const visible = showHidden ? cases : cases.filter((c) => !c.isHidden);

  return (
    <section className={cn("space-y-3", className)} aria-label="Test cases and examples">
      <h2 className="text-sm font-semibold">Test Cases &amp; Examples</h2>

      {visible.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No public testcases available yet for this problem.
        </p>
      ) : null}

      {visible.map((c, i) => (
        <div key={c.id} className="space-y-2 rounded-lg border bg-card p-3 text-xs">
          <div className="flex items-center gap-2">
            <p className="font-mono font-medium text-primary">Test Case {i + 1}</p>
            {c.isHidden ? (
              <Badge variant="secondary" className="font-normal">
                hidden
              </Badge>
            ) : null}
          </div>

          {c.input === "" && c.expectedOutput === "" ? (
            <p className="text-muted-foreground">
              Test case content is not available yet.
            </p>
          ) : (
            <>
              <p>
                <span className="text-muted-foreground">Input: </span>
                <span className="whitespace-pre-wrap font-mono text-cyan-600 dark:text-cyan-300">
                  {c.input}
                </span>
              </p>
              <p>
                <span className="text-muted-foreground">Output: </span>
                <span className="whitespace-pre-wrap font-mono">{c.expectedOutput}</span>
              </p>
            </>
          )}

          <p className="text-muted-foreground">
            Explanation: {c.explanation ?? "N/A"}
          </p>
        </div>
      ))}
    </section>
  );
}
