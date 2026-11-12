import { StudyPlanEditor } from "@/components/admin/admin-study-plans";
import { AdminGuard } from "@/components/layout/role-guard";

// Next 14: params là object thường (từ Next 15 mới là Promise). "/admin/study-plans/new" là route tĩnh, được ưu tiên.
export default function EditStudyPlanPage({ params }: { params: { slug: string } }) {
  return (
    <AdminGuard>
      <StudyPlanEditor slug={params.slug} />
    </AdminGuard>
  );
}
