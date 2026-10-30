"use client";

import { Pencil, ThumbsDown, ThumbsUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { PROBLEM_DIFFICULTY_META, type ProblemDifficulty } from "./problem-types";

export interface ProblemHeaderEditor {
  /** Current draft title. */
  title: string;
  onTitleChange: (value: string) => void;
  /** Whether the title is currently shown as an editable field. */
  editing: boolean;
  /** Enter edit mode. */
  onStartEdit: () => void;
}

export interface ProblemHeaderProps {
  id: number;
  title: string;
  difficulty: ProblemDifficulty;
  status?: string;
  likes?: number | null;
  dislikes?: number | null;
  /** When provided, the title becomes editable. */
  editor?: ProblemHeaderEditor;
  /** Extra controls rendered alongside the votes, e.g. a watchlist toggle. */
  actions?: React.ReactNode;
  className?: string;
}

/**
 * Title, difficulty, lifecycle status and vote counts for a problem.
 */
export function ProblemHeader({
  id,
  title,
  difficulty,
  status,
  likes,
  dislikes,
  editor,
  actions,
  className,
}: ProblemHeaderProps) {
  const diff = PROBLEM_DIFFICULTY_META[difficulty];

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        {editor?.editing ? (
          <Input
            value={editor.title}
            onChange={(e) => editor.onTitleChange(e.target.value)}
            aria-label="Problem title"
            className="h-auto max-w-md text-heading-md"
          />
        ) : (
          <h1 className="text-heading-lg text-ink">{title}</h1>
        )}

        <div className="flex shrink-0 items-center gap-2">
          {editor && !editor.editing ? (
            <Button variant="ghost" size="icon" aria-label="Edit problem" onClick={editor.onStartEdit}>
              <Pencil className="h-4 w-4" aria-hidden="true" />
            </Button>
          ) : null}
          {status ? (
            <Badge variant="secondary" className="font-mono font-normal">
              {status}
            </Badge>
          ) : null}
          <Badge variant="outline" className={cn("font-normal", diff.className)}>
            {diff.label}
          </Badge>
        </div>
      </div>

      <div className="flex items-center gap-4 text-body-sm text-mute">
        <span className="font-mono">#{id}</span>
        {likes != null ? (
          <span className="flex items-center gap-1">
            <ThumbsUp className="h-3.5 w-3.5" aria-hidden="true" />
            {likes}
          </span>
        ) : null}
        {dislikes != null ? (
          <span className="flex items-center gap-1">
            <ThumbsDown className="h-3.5 w-3.5" aria-hidden="true" />
            {dislikes}
          </span>
        ) : null}
        {actions}
      </div>
    </div>
  );
}
