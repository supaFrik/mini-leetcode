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
 * Public sample testcases rendered as hairline panels.
 *
 * Hidden cases are flagged rather than hidden outright when `showHidden` is
 * on, so the sample list still communicates how many cases the judge runs.
 */
export function ProblemTestCases({ cases, showHidden = true, className }: ProblemTestCasesProps) {
  const visible = showHidden ? cases : cases.filter((c) => !c.isHidden);

  return (
    <section className={cn("space-y-3", className)} aria-label="Test cases and examples">
      <h2 className="text-heading-md text-ink">Test cases &amp; examples</h2>

      {visible.length === 0 ? (
        <p className="text-body-md text-mute">No public testcases available yet for this problem.</p>
      ) : null}

      {visible.map((c, i) => (
        <div key={c.id} className="space-y-2 rounded-lg border border-border bg-card p-4 text-body-sm">
          <div className="flex items-center gap-2">
            <p className="font-mono font-medium text-ink">Test case {i + 1}</p>
            {c.isHidden ? (
              <Badge variant="secondary" className="font-mono">
                hidden
              </Badge>
            ) : null}
          </div>

          {c.input === "" && c.expectedOutput === "" ? (
            <p className="text-mute">Test case content is not available yet.</p>
          ) : (
            <>
              <p>
                <span className="text-mute">Input: </span>
                <span className="whitespace-pre-wrap font-mono text-ink">{c.input}</span>
              </p>
              <p>
                <span className="text-mute">Output: </span>
                <span className="whitespace-pre-wrap font-mono text-ink">{c.expectedOutput}</span>
              </p>
            </>
          )}

          <p className="text-mute">Explanation: {c.explanation ?? "N/A"}</p>
        </div>
      ))}
    </section>
  );
}
