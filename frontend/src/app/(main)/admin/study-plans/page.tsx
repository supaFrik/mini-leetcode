import { AdminStudyPlansView } from "@/components/admin/admin-study-plans";
import { AdminGuard } from "@/components/layout/role-guard";

export default function AdminStudyPlansPage() {
  return (
    <AdminGuard>
      <AdminStudyPlansView />
    </AdminGuard>
  );
}
