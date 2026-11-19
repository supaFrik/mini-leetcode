import { AdminRolesView } from "@/components/admin/admin-roles";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminRolesPage() {
  return (
    <RoleGuard allow={["ADMIN"]}>
      <AdminRolesView />
    </RoleGuard>
  );
}
