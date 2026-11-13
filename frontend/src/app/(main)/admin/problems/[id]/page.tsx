import { notFound } from "next/navigation";
import { AdminProblemPage } from "@/components/admin/admin-problem-page";
import { AdminGuard } from "@/components/layout/role-guard";

// "/admin/problems/new" là route tĩnh, được ưu tiên hơn [id].
export default function EditProblemPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  if (!Number.isInteger(id) || id <= 0) notFound();
  return (
    <AdminGuard>
      <AdminProblemPage id={id} />
    </AdminGuard>
  );
}
