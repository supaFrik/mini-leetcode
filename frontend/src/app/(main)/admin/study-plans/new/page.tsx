import { StudyPlanEditor } from "@/components/admin/admin-study-plans";
import { AdminGuard } from "@/components/layout/role-guard";

export default function NewStudyPlanPage() {
  return (
    <AdminGuard>
      <StudyPlanEditor />
    </AdminGuard>
  );
}
