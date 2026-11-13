import { TopicEditor } from "@/components/admin/admin-topics";
import { AdminGuard } from "@/components/layout/role-guard";

// Next 14: params là object thường. "/admin/topics/new" là route tĩnh, được ưu tiên.
export default function EditTopicPage({ params }: { params: { slug: string } }) {
  return (
    <AdminGuard>
      <TopicEditor slug={params.slug} />
    </AdminGuard>
  );
}
