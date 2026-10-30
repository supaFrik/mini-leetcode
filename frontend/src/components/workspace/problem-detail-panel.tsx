"use client";

import { useState } from "react";
import { Star } from "lucide-react";
import { ProblemDetail } from "@/components/blocks/problem-detail";
import { errorMessage } from "@/lib/api";
import { updateProblem } from "@/lib/services";
import { cn } from "@/lib/utils";
import type { CaseResult, ProblemFull, Submission, TestCase } from "@/types/api";

export interface ProblemDetailPanelProps {
  problem: ProblemFull;
  cases: TestCase[];
  canEdit?: boolean;
  onProblemChange?: (p: ProblemFull) => void;
  result?: Submission | null;
  caseResults?: CaseResult[] | null;
  judging?: boolean;
  timedOut?: boolean;
  error?: { message: string } | null;
  runMode?: boolean;
  showHiddenCases?: boolean;
  showTestCases?: boolean;
  showResult?: boolean;
  className?: string;
}

/**
 * App-side adapter for the `problem-detail` registry block.
 *
 * The block is deliberately domain-agnostic: it declares its own props and
 * types. This wrapper maps the application's `ProblemFull` / `TestCase` /
 * `Submission` models onto the block and owns the instructor editing flow
 * (draft state, save request, error surfacing).
 */
export function ProblemDetailPanel({
  problem,
  cases,
  canEdit = false,
  onProblemChange,
  result = null,
  caseResults = null,
  judging = false,
  timedOut = false,
  error = null,
  runMode = false,
  showHiddenCases = true,
  showTestCases = true,
  showResult = true,
  className,
}: ProblemDetailPanelProps) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(problem.title);
  const [description, setDescription] = useState(problem.description);
  const [watch, setWatch] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const startEdit = () => {
    setTitle(problem.title);
    setDescription(problem.description);
    setErr(null);
    setEditing(true);
  };

  const cancelEdit = () => {
    setTitle(problem.title);
    setDescription(problem.description);
    setErr(null);
    setEditing(false);
  };

  const save = async () => {
    setBusy(true);
    setErr(null);
    try {
      const updated = await updateProblem(problem.id, { title, description });
      const next = { ...problem, ...updated, title, description };
      onProblemChange?.(next);
      setEditing(false);
    } catch (e) {
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ProblemDetail
      className={className}
      problem={{
        id: problem.id,
        title: editing ? title : problem.title,
        description: editing ? description : problem.description,
        difficulty: problem.difficulty,
        timeLimitMs: problem.timeLimitMs,
        memoryLimitMb: problem.memoryLimitMb,
        status: problem.status,
        likes: problem.likes,
        dislikes: problem.dislikes,
      }}
      cases={cases}
      result={result}
      caseResults={caseResults}
      judging={judging}
      timedOut={timedOut}
      error={error}
      runMode={runMode}
      showHiddenCases={showHiddenCases}
      showTestCases={showTestCases}
      showResult={showResult}
      editor={
        canEdit
          ? {
              title,
              onTitleChange: setTitle,
              description,
              onDescriptionChange: setDescription,
              editing,
              onStartEdit: startEdit,
              onSave: save,
              onCancel: cancelEdit,
              busy,
              error: err,
            }
          : undefined
      }
      actions={
        <button
          type="button"
          className="flex items-center gap-1 hover:text-foreground"
          aria-pressed={watch}
          onClick={() => setWatch(!watch)}
        >
          <Star
            className={cn(
              "h-3.5 w-3.5",
              watch ? "fill-amber-400 text-amber-400" : "text-mute"
            )}
            aria-hidden="true"
          />
          Watchlist
        </button>
      }
    />
  );
}
