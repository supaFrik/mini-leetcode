import { cn } from "@/lib/utils";
import type { Difficulty } from "@/types/api";

const STYLE: Record<Difficulty, string> = {
  EASY: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
  MEDIUM: "bg-amber-500/10 text-amber-400 border-amber-500/30",
  HARD: "bg-red-500/10 text-red-400 border-red-500/30",
};
const LABEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };

export function DifficultyBadge({ value, className }: { value: Difficulty; className?: string }) {
  return <span className={cn("rounded border px-2 py-0.5 font-mono text-xs", STYLE[value], className)}>{LABEL[value]}</span>;
}