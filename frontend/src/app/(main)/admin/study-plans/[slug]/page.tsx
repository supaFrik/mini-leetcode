import { Suspense } from "react";
import { CourseBuilder } from "@/components/admin/course-builder/course-builder";
import { AdminGuard } from "@/components/layout/role-guard";

// Next 14: params là object thường. "/admin/study-plans/new" là route tĩnh, được ưu tiên.
export default function EditStudyPlanPage({ params }: { params: { slug: string } }) {
  return (
    <AdminGuard>
      <Suspense fallback={<div className="mx-auto max-w-container px-4 py-6 text-mute">Loading study plan…</div>}>
        <CourseBuilder slug={params.slug} />
      </Suspense>
    </AdminGuard>
  );
}
