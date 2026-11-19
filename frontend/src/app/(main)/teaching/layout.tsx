import { AdminSidebar } from "@/components/admin/admin-nav";
import { RoleGuard } from "@/components/layout/role-guard";

// Khung /teaching giống /admin: sidebar Teaching bên trái (AdminSidebar tự chọn menu theo role).
export default function TeachingLayout({ children }: { children: React.ReactNode }) {
  return (
    <RoleGuard allow={["INSTRUCTOR", "ADMIN"]}>
      <div className="flex h-full">
        <AdminSidebar />
        <div className="min-w-0 flex-1 overflow-auto">{children}</div>
      </div>
    </RoleGuard>
  );
}
