import { Badge } from "@/components/ui/badge";
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
    <Badge
      variant="outline"
      className={cn("font-mono", v.ok ? "border-success/25 bg-success/10 text-success" : "border-destructive/25 bg-destructive/10 text-destructive")}
    >
      {v.short}
    </Badge>
  );
}
