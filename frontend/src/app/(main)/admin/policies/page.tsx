import { AdminPoliciesView } from "@/components/admin/admin-policies";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminPoliciesPage() {
  return (
    <RoleGuard allow={["INSTRUCTOR", "ADMIN"]}>
      <AdminPoliciesView />
    </RoleGuard>
  );
}
