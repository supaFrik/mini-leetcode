import { cn } from "@/lib/utils";
import type { Difficulty } from "@/types/api";
import { difficultyMeta } from "./difficulty";

/** Solid pill with white text; labels and colors come from ./difficulty. */
export function DifficultyBadge({ value, className }: { value: Difficulty | string; className?: string }) {
  const { label, color } = difficultyMeta(value);
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 font-sans text-[12px] font-medium leading-5 text-white select-none",
        className
      )}
      style={{ backgroundColor: color }}
    >
      {label}
    </span>
  );
}
