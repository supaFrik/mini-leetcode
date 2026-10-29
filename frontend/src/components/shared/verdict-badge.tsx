import { cn } from "@/lib/utils";
import { VERDICT } from "@/lib/verdict";
import type { SubmissionStatus, Verdict } from "@/types/api";

export function VerdictBadge({ verdict, status }: { verdict: Verdict | null; status?: SubmissionStatus }) {
  if (!verdict) return <span className="font-mono text-xs text-muted-foreground">{status === "FAILED" ? "SYSTEM ERROR" : (status ?? "—")}</span>;
  const v = VERDICT[verdict];
  return (
    <span className={cn("rounded border px-2 py-0.5 font-mono text-xs",
      v.ok ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" : "border-red-500/30 bg-red-500/10 text-red-600 dark:text-red-400")}>
      {v.short}
    </span>
  );
}
