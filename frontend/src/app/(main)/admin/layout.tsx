import { AdminSidebar } from "@/components/admin/admin-nav";
import { RoleGuard } from "@/components/layout/role-guard";

// Khung chung cho mọi trang /admin: sidebar trái, nội dung bên phải. Từng trang vẫn tự guard theo role.
// Dùng RoleGuard (không bọc thêm div) để min-h-full tính theo <main>.
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <RoleGuard allow={["INSTRUCTOR", "ADMIN"]}>
      <div className="flex min-h-full">
        <AdminSidebar />
        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </RoleGuard>
  );
}
