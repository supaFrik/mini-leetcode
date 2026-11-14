import { AdminUsersView } from "@/components/admin/admin-users-view";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminUsersPage() {
  return (
    <RoleGuard allow={["ADMIN"]}>
      <AdminUsersView />
    </RoleGuard>
  );
}
