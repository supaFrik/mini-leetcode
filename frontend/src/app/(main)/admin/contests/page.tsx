import { AdminContestsView } from "@/components/admin/admin-contests-view";
import { AdminGuard } from "@/components/layout/role-guard";

export default function AdminContestsPage() {
  return (
    <AdminGuard>
      <AdminContestsView />
    </AdminGuard>
  );
}
