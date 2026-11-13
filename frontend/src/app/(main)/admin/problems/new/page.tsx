import { NewProblem } from "@/components/admin/admin-problem-page";
import { AdminGuard } from "@/components/layout/role-guard";

export default function NewProblemPage() {
  return (
    <AdminGuard>
      <NewProblem />
    </AdminGuard>
  );
}
