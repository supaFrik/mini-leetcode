import { NewStudyPlan } from "@/components/admin/course-builder/new-plan";
import { AdminGuard } from "@/components/layout/role-guard";

export default function NewStudyPlanPage() {
  return (
    <AdminGuard>
      <NewStudyPlan />
    </AdminGuard>
  );
}
