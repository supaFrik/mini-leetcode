import { Suspense } from "react";
import { ContestEditorPage } from "@/components/admin/contest-editor-page";
import { AdminGuard } from "@/components/layout/role-guard";

export default function EditContestRoute({ params }: { params: { id: string } }) {
  const contestId = Number.parseInt(params.id, 10);
  return (
    <AdminGuard>
      <Suspense fallback={null}>
        <ContestEditorPage contestId={Number.isNaN(contestId) ? null : contestId} />
      </Suspense>
    </AdminGuard>
  );
}
