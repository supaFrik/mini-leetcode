import type { Verdict } from "@/types/api";

export const VERDICT: Record<Verdict, { label: string; ok: boolean }> = {
  AC: { label: "Accepted", ok: true },
  WA: { label: "Wrong Answer", ok: false },
  TLE: { label: "Time Limit Exceeded", ok: false },
  MLE: { label: "Memory Limit Exceeded", ok: false },
  CE: { label: "Compilation Error", ok: false },
  RE: { label: "Runtime Error", ok: false },
  SV: { label: "Security Violation", ok: false },
  FLAGGED_CHEATING: { label: "Flagged (Cheating)", ok: false },
};