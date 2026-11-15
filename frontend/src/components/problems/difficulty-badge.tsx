import { cn } from "@/lib/utils";
import type { Difficulty } from "@/types/api";

const RANK: Record<Difficulty, number> = {
  EASY: 1,
  MEDIUM: 2,
  HARD: 3,
};

const LABEL: Record<Difficulty, string> = {
  EASY: "Easy",
  MEDIUM: "Medium",
  HARD: "Hard",
};

/**
 * Text color for each difficulty level:
 * Easy -> Emerald / Green
 * Medium -> Amber / Orange (matches the screenshot text)
 * Hard -> Red / Rose
 */
const TEXT_COLOR: Record<Difficulty, string> = {
  EASY: "text-emerald-600 dark:text-emerald-500",
  MEDIUM: "text-amber-600 dark:text-amber-500",
  HARD: "text-red-600 dark:text-red-500",
};

export function DifficultyBadge({
  value,
  className,
}: {
  value: Difficulty | string;
  className?: string;
}) {
  const normalized = (value ? String(value).toUpperCase() : "EASY") as Difficulty;
  const rank = RANK[normalized] ?? 1;
  const label = LABEL[normalized] ?? value;
  const textColor = TEXT_COLOR[normalized] ?? "text-muted-foreground";

  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 font-sans text-[13px] font-medium leading-none select-none",
        textColor,
        className
      )}
      aria-label={`Difficulty: ${label}`}
    >
      <span className="flex items-end gap-[2px] h-[14px]" aria-hidden="true">
        <span
          className={cn(
            "inline-block w-1 rounded-[1px] transition-colors",
            "h-[6px]",
            rank >= 1 ? "bg-current" : "bg-[#cdd2d8] dark:bg-neutral-700"
          )}
        />
        <span
          className={cn(
            "inline-block w-1 rounded-[1px] transition-colors",
            "h-[10px]",
            rank >= 2 ? "bg-current" : "bg-[#cdd2d8] dark:bg-neutral-700"
          )}
        />
        <span
          className={cn(
            "inline-block w-1 rounded-[1px] transition-colors",
            "h-[14px]",
            rank >= 3 ? "bg-current" : "bg-[#cdd2d8] dark:bg-neutral-700"
          )}
        />
      </span>
      <span>{label}</span>
    </span>
  );
}
