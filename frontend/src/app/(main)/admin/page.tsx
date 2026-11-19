import { AdminOverviewView } from "@/components/admin/admin-overview";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminOverviewPage() {
  return (
    <RoleGuard allow={["ADMIN"]}>
      <AdminOverviewView />
    </RoleGuard>
  );
}
