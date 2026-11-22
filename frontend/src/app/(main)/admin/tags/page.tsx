import { AdminTagsView } from "@/components/admin/admin-tags";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminTagsPage() {
  return (
    <RoleGuard allow={["INSTRUCTOR", "ADMIN"]}>
      <AdminTagsView />
    </RoleGuard>
  );
}
