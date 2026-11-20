import type { Difficulty } from "@/types/api";

/** Single source for difficulty order, labels and badge colors (easiest first). */
export const DIFFICULTY_LEVELS: { value: Difficulty; label: string; color: string }[] = [
  { value: "BEGINNER", label: "Beginner", color: "#0F6E56" },
  { value: "EASY", label: "Easy", color: "#3B6D11" },
  { value: "MEDIUM", label: "Medium", color: "#854F0B" },
  { value: "HARD", label: "Hard", color: "#993C1D" },
  { value: "EXPERT", label: "Expert", color: "#A32D2D" },
];

const UNKNOWN_COLOR = "#5F6368";

/** Meta for a value from the API; unknown values keep their raw text on a neutral gray. */
export function difficultyMeta(value: string | null | undefined) {
  const key = String(value ?? "").toUpperCase();
  return DIFFICULTY_LEVELS.find((l) => l.value === key) ?? { value: key, label: value ? String(value) : "Unknown", color: UNKNOWN_COLOR };
}
