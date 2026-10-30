"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

export interface ProblemDescriptionProps {
  /** Markdown source. */
  markdown: string;
  timeLimitMs?: number;
  memoryLimitMb?: number;
  /** When provided, renders an editable markdown editor instead of the rendered statement. */
  editor?: {
    value: string;
    onChange: (value: string) => void;
    /** Whether the editor is currently open. */
    editing: boolean;
    onSave: () => void;
    onCancel: () => void;
    busy?: boolean;
    error?: string | null;
  };
  className?: string;
}

/**
 * Markdown problem statement with the judge's resource limits.
 *
 * Uses `prose` from `@tailwindcss/typography` for the body and shadcn
 * `Badge` for the limit chips. When an `editor` is supplied the statement is
 * replaced by an editable markdown textarea, which is how instructors revise
 * a problem from the same surface.
 */
export function ProblemDescription({
  markdown,
  timeLimitMs,
  memoryLimitMb,
  editor,
  className,
}: ProblemDescriptionProps) {
  return (
    <div className={cn("space-y-4", className)}>
      {timeLimitMs != null || memoryLimitMb != null ? (
        <div className="flex flex-wrap gap-2">
          {timeLimitMs != null ? (
            <Badge variant="outline" className="font-mono font-normal">
              Time: {timeLimitMs} ms
            </Badge>
          ) : null}
          {memoryLimitMb != null ? (
            <Badge variant="outline" className="font-mono font-normal">
              Memory: {memoryLimitMb} MB
            </Badge>
          ) : null}
        </div>
      ) : null}

      {editor?.editing ? (
        <div className="space-y-2">
          <Textarea
            value={editor.value}
            onChange={(e) => editor.onChange(e.target.value)}
            aria-label="Markdown content"
            className="min-h-56 font-mono text-xs"
          />
          {editor.error ? (
            <p role="alert" className="text-xs text-destructive">
              {editor.error}
            </p>
          ) : null}
          <div className="flex gap-2">
            <Button size="sm" onClick={editor.onSave} disabled={editor.busy}>
              Save changes
            </Button>
            <Button size="sm" variant="ghost" onClick={editor.onCancel}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <article
          className={cn(
            "prose prose-sm max-w-none dark:prose-invert",
            "prose-code:rounded prose-code:bg-secondary prose-code:px-1 prose-code:py-0.5 prose-code:font-mono",
            "prose-code:text-emerald-600 prose-code:before:content-none prose-code:after:content-none",
            "dark:prose-code:text-emerald-400",
            "prose-pre:bg-secondary"
          )}
        >
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown}</ReactMarkdown>
        </article>
      )}
    </div>
  );
}
