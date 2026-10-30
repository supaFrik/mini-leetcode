"use client";

import { cn } from "@/lib/utils";

export interface ProblemDiffViewProps {
  expected: string;
  actual: string;
  className?: string;
}

interface DiffRow {
  text?: string;
  diff: boolean;
}

function toLines(value: string): string[] {
  return value.replace(/\r\n/g, "\n").replace(/\n$/, "").split("\n");
}

function DiffColumn({ title, rows, tone }: { title: string; rows: DiffRow[]; tone: "red" | "green" }) {
  return (
    <div className="min-w-0">
      <p className="eyebrow mb-1.5">{title}</p>
      <div className="overflow-auto rounded-sm border border-border bg-card py-1 font-mono text-code text-ink">
        {rows.map((row, i) => (
          <div
            key={i}
            className={cn(
              "flex gap-2 px-2 py-0.5",
              row.diff && (tone === "red" ? "bg-destructive/10" : "bg-success/10")
            )}
          >
            <span className="w-4 shrink-0 select-none text-right text-faint">{i + 1}</span>
            {row.text === undefined ? (
              <span className="italic text-faint">(missing line)</span>
            ) : (
              <span className="whitespace-pre-wrap break-all">{row.text === "" ? " " : row.text}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Side-by-side expected vs. actual output.
 *
 * Compares line by line, ignoring trailing whitespace on each line, which
 * matches how the judge compares program output.
 */
export function ProblemDiffView({ expected, actual, className }: ProblemDiffViewProps) {
  const expectedLines = toLines(expected);
  const actualLines = toLines(actual);
  const total = Math.max(expectedLines.length, actualLines.length);

  const rows = Array.from({ length: total }, (_, i) => ({
    expected: expectedLines[i],
    actual: actualLines[i],
    diff: expectedLines[i]?.trimEnd() !== actualLines[i]?.trimEnd(),
  }));

  return (
    <div className={cn("grid grid-cols-1 gap-3 sm:grid-cols-2", className)}>
      <DiffColumn title="Output (your code)" tone="red" rows={rows.map((r) => ({ text: r.actual, diff: r.diff }))} />
      <DiffColumn title="Expected output" tone="green" rows={rows.map((r) => ({ text: r.expected, diff: r.diff }))} />
    </div>
  );
}
