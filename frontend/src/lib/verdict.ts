import type { Verdict } from "@/types/api";

export const VERDICT: Record<Verdict, { label: string; short: string; ok: boolean }> = {
  AC: { label: "Accepted", short: "AC", ok: true },
  WA: { label: "Wrong Answer", short: "WA", ok: false },
  TLE: { label: "Time Limit Exceeded", short: "TLE", ok: false },
  MLE: { label: "Memory Limit Exceeded", short: "MLE", ok: false },
  CE: { label: "Compilation Error", short: "CE", ok: false },
  RE: { label: "Runtime Error", short: "RE", ok: false },
  SV: { label: "Security Violation", short: "SV", ok: false },
  FLAGGED_CHEATING: { label: "Flagged (Cheating)", short: "FLAG", ok: false },
};
