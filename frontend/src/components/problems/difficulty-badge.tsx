import { cn } from "@/lib/utils";
import type { Difficulty } from "@/types/api";

/**
 * Difficulty keeps its semantic colours — a learner reads green/amber/red
 * instantly, and folding it into the ink ladder would cost that signal. The
 * colours are desaturated to sit on the near-white canvas without shouting.
 */
const STYLE: Record<Difficulty, string> = {
  EASY: "border-easy/25 bg-easy/10 text-easy",
  MEDIUM: "border-medium/25 bg-medium/10 text-[#8a5a00]",
  HARD: "border-hard/25 bg-hard/10 text-hard",
};
const LABEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };

export function DifficultyBadge({ value, className }: { value: Difficulty; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 font-mono text-body-sm font-medium",
        STYLE[value],
        className
      )}
    >
      {LABEL[value]}
    </span>
  );
}
