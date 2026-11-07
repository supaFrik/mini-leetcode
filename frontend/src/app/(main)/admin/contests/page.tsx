import { Suspense } from "react";
import { AdminContestsView } from "@/components/admin/admin-contests-view";
import { AdminGuard } from "@/components/layout/role-guard";

export default function AdminContestsPage() {
  return (
    <AdminGuard>
      <Suspense fallback={null}>
        <AdminContestsView />
      </Suspense>
    </AdminGuard>
  );
}
