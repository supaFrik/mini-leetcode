import { cn } from "@/lib/utils";
import { VERDICT } from "@/lib/verdict";
import type { SubmissionStatus, Verdict } from "@/types/api";

export function VerdictBadge({ verdict, status }: { verdict: Verdict | null; status?: SubmissionStatus }) {
  if (!verdict) {
    return (
      <span className="font-mono text-body-sm text-mute">
        {status === "FAILED" ? "SYSTEM ERROR" : (status ?? "—")}
      </span>
    );
  }
  const v = VERDICT[verdict];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 font-mono text-body-sm font-medium",
        v.ok ? "border-success/25 bg-success/10 text-success" : "border-destructive/25 bg-destructive/10 text-destructive"
      )}
    >
      {v.short}
    </span>
  );
}
