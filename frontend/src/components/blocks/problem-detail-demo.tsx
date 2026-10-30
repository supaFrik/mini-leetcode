"use client";

import { useState } from "react";
import { ProblemDetail } from "./problem-detail";
import type {
  ProblemDetailCaseResult,
  ProblemDetailProblem,
  ProblemDetailTestCase,
} from "./problem-types";

const PROBLEM: ProblemDetailProblem = {
  id: 1,
  title: "Two Sum",
  difficulty: "EASY",
  description: [
    "Given an array of integers `nums` and an integer `target`, return the indices",
    "of the two numbers such that they add up to `target`.",
    "",
    "### Constraints",
    "",
    "- `2 <= nums.length <= 10^4`",
    "- `-10^9 <= nums[i] <= 10^9`",
  ].join("\n"),
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  status: "PUBLISHED",
  likes: 1540,
  dislikes: 42,
};

const CASES: ProblemDetailTestCase[] = [
  {
    id: 101,
    input: "nums = [2,7,11,15], target = 9",
    expectedOutput: "[0,1]",
    isHidden: false,
    explanation: "nums[0] + nums[1] == 9",
  },
  {
    id: 102,
    input: "nums = [3,2,4], target = 6",
    expectedOutput: "[1,2]",
    isHidden: false,
  },
  {
    id: 103,
    input: "nums = [3,3], target = 6",
    expectedOutput: "[0,1]",
    isHidden: true,
  },
];

const FAILING_CASES: ProblemDetailCaseResult[] = [
  {
    index: 0,
    verdict: "AC",
    hidden: false,
    input: "nums = [2,7,11,15], target = 9",
    expected: "[0,1]",
    actual: "[0,1]",
    timeMs: 12,
  },
  {
    index: 1,
    verdict: "WA",
    hidden: false,
    input: "nums = [3,2,4], target = 6",
    expected: "[1,2]",
    actual: "[0,2]",
    timeMs: 9,
  },
  {
    index: 2,
    verdict: "SKIPPED",
    hidden: true,
  },
];

/**
 * Standalone demo of the `problem-detail` block.
 */
export default function ProblemDetailDemo() {
  const [mode, setMode] = useState<"empty" | "accepted" | "failed">("empty");

  return (
    <div className="min-h-screen bg-background p-6">
      <div className="mx-auto mb-4 flex max-w-3xl items-center gap-2">
        {(["empty", "accepted", "failed"] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            className={
              mode === m
                ? "rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground"
                : "rounded-md border px-3 py-1.5 text-sm text-muted-foreground"
            }
          >
            {m}
          </button>
        ))}
      </div>

      <div className="mx-auto max-w-3xl overflow-hidden rounded-lg border bg-card">
        <ProblemDetail
          problem={PROBLEM}
          cases={CASES}
          result={
            mode === "empty"
              ? null
              : {
                  status: "COMPLETED",
                  verdict: mode === "accepted" ? "AC" : "WA",
                  executionTimeMs: mode === "accepted" ? 42 : 39,
                  memoryUsedKb: 18400,
                  passTestCases: mode === "accepted" ? 3 : 1,
                  totalTestCases: 3,
                  errorMessage: null,
                }
          }
          caseResults={mode === "empty" ? null : FAILING_CASES}
          runMode={mode === "accepted"}
        />
      </div>
    </div>
  );
}
