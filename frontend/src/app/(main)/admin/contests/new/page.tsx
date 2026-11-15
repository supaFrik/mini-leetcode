import { Suspense } from "react";
import { ContestEditorPage } from "@/components/admin/contest-editor-page";
import { AdminGuard } from "@/components/layout/role-guard";

export default function NewContestRoute() {
  return (
    <AdminGuard>
      <Suspense fallback={null}>
        <ContestEditorPage />
      </Suspense>
    </AdminGuard>
  );
}
