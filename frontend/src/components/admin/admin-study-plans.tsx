"use client";
import Link from "next/link";
import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useLoad } from "@/hooks/use-load";
import { useCanEdit } from "@/hooks/use-can-edit";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import { deleteStudyPlan, fetchStudyPlans } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { StudyPlanSummary } from "@/types/api";
import { PLAN_STATUS_VARIANT } from "./status";

/** /admin/study-plans: danh sách plan; soạn plan ở Course Builder (/admin/study-plans/[slug]). */
export function AdminStudyPlansView() {
  const [version, setVersion] = useState(0);
  const load = useLoad(fetchStudyPlans, [version]);
  const canEdit = useCanEdit();
  const confirm = useConfirm();

  async function remove(plan: StudyPlanSummary) {
    const ok = await confirm({
      title: `Delete study plan "${plan.title}"?`,
      description: "Learner progress on this plan is lost. Its problems stay in the problem bank.",
      action: "Delete",
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteStudyPlan(plan.slug);
      toast(`Study plan "${plan.title}" deleted.`, "success");
      setVersion((v) => v + 1);
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  }

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex justify-end">
        <Button asChild>
          <Link href="/admin/study-plans/new"><Plus className="h-4 w-4" />New plan</Link>
        </Button>
      </div>

      <div className="overflow-hidden rounded-sm border border-border bg-card" aria-label="Study plans" role="region">
        {load.state === "loading" && <p className="p-6 text-body-md text-mute">Loading study plans…</p>}
        {load.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{load.message}</p>}
        {load.state === "ok" && load.data.length === 0 && (
          <p className="p-6 text-body-md text-mute">No study plans yet. Create the first one.</p>
        )}
        {load.state === "ok" && load.data.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="px-4 py-2 font-medium">Plan</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="hidden px-4 py-2 font-medium sm:table-cell">Content</th>
                <th className="hidden px-4 py-2 font-medium md:table-cell">Author</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {load.data.map((plan) => (
                <tr key={plan.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <Link href={`/admin/study-plans/${plan.slug}`} className="font-medium text-ink hover:underline">{plan.title}</Link>
                    <div className="font-mono text-body-sm text-mute">{plan.slug}</div>
                  </td>
                  <td className="px-4 py-3"><Badge variant={PLAN_STATUS_VARIANT[plan.status]}>{plan.status}</Badge></td>
                  <td className="hidden px-4 py-3 text-mute sm:table-cell">{plan.itemCount} items · {plan.stageCount} stages</td>
                  <td className="hidden px-4 py-3 text-mute md:table-cell">{plan.createdByUsername ?? "—"}</td>
                  <td className="px-4 py-3 text-right">
                    {!canEdit(plan.createdById) && <span className="mr-3 text-body-sm text-mute">Read-only</span>}
                    <Link href={`/study-plans/${plan.slug}`} className="text-body-sm text-link hover:underline">Preview</Link>
                    {canEdit(plan.createdById) && (
                      <Button variant="ghost" size="icon-sm" className="ml-2" aria-label={`Delete ${plan.title}`} onClick={() => void remove(plan)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
