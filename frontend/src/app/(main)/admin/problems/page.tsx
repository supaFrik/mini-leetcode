import { Suspense } from "react";
import { AdminProblemsView } from "@/components/admin/admin-problems-view";
import { AdminGuard } from "@/components/layout/role-guard";

export default function AdminProblemsPage() {
  return (
    <AdminGuard>
      <Suspense fallback={null}>
        <AdminProblemsView />
      </Suspense>
    </AdminGuard>
  );
}
