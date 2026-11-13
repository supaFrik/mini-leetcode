import { TopicEditor } from "@/components/admin/admin-topics";
import { AdminGuard } from "@/components/layout/role-guard";

export default function NewTopicPage() {
  return (
    <AdminGuard>
      <TopicEditor />
    </AdminGuard>
  );
}
