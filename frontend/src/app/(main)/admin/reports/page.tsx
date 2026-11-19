import { Suspense } from "react";
import { AdminReportsView } from "@/components/admin/admin-reports";

// Suspense: AdminReportsView đọc ?contest= bằng useSearchParams.
export default function AdminReportsPage() {
  return (
    <Suspense>
      <AdminReportsView />
    </Suspense>
  );
}
