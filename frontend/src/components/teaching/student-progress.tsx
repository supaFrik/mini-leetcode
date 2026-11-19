"use client";
import { useLoad } from "@/hooks/use-load";
import { fetchTeachingProgress } from "@/lib/services";
import { cn } from "@/lib/utils";
import type { ProgressStatus } from "@/types/api";

const CELL: Record<ProgressStatus, { label: string; className: string }> = {
  DONE: { label: "Done", className: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300" },
  IN_PROGRESS: { label: "In progress", className: "bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300" },
  STUCK: { label: "Stuck", className: "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300" },
  NOT_STARTED: { label: "—", className: "text-mute" },
};

/** /teaching/progress: học viên × stage cho từng study plan của giảng viên. */
export function StudentProgressView() {
  const load = useLoad(fetchTeachingProgress, []);
  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      {load.state === "loading" && <p className="text-body-md text-mute">Loading…</p>}
      {load.state === "error" && <p className="text-body-md text-destructive" role="alert">{load.message}</p>}
      {load.state === "ok" && load.data.length === 0 && <p className="text-body-md text-mute">You have not created any study plans yet.</p>}
      {load.state === "ok" && load.data.map((plan) => (
        <section key={plan.planId} className="space-y-2">
          <h2 className="text-heading-sm text-ink">{plan.title}</h2>
          <div className="overflow-x-auto rounded-sm border border-border bg-card" role="region" aria-label={`Progress in ${plan.title}`}>
            {plan.learners.length === 0 ? (
              <p className="p-6 text-body-md text-mute">No learners have submitted to this plan yet.</p>
            ) : (
              <table className="w-full text-left text-body-sm">
                <thead className="border-b border-border text-mute">
                  <tr>
                    <th className="px-4 py-2 font-medium">Learner</th>
                    {plan.stages.map((s, i) => (
                      <th key={s.problemId} className="px-2 py-2 font-medium" title={s.title}>Stage {i + 1}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {plan.learners.map((l) => (
                    <tr key={l.userId} className="border-b border-border last:border-0">
                      <td className="px-4 py-2 font-medium text-ink">{l.username}</td>
                      {l.stages.map((st, i) => (
                        <td key={plan.stages[i].problemId} className="px-2 py-1.5">
                          <span className={cn("inline-block rounded-sm px-1.5 py-0.5 whitespace-nowrap", CELL[st].className)}>
                            {CELL[st].label}
                          </span>
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
      ))}
    </div>
  );
}
