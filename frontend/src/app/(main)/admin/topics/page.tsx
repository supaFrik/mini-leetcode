import { AdminTopicsView } from "@/components/admin/admin-topics";
import { AdminGuard } from "@/components/layout/role-guard";

export default function AdminTopicsPage() {
  return (
    <AdminGuard>
      <AdminTopicsView />
    </AdminGuard>
  );
}
