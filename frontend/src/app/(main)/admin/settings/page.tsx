import { AdminSettingsView } from "@/components/admin/admin-settings";
import { RoleGuard } from "@/components/layout/role-guard";

export default function AdminSettingsPage() {
  return (
    <RoleGuard allow={["ADMIN"]}>
      <AdminSettingsView />
    </RoleGuard>
  );
}
