import { StudyPlanDetailView } from "@/components/study-plans/study-plan-views";

// Next 14: params là object thường (từ Next 15 mới là Promise)
export default function StudyPlanPage({ params }: { params: { slug: string } }) {
  return <StudyPlanDetailView slug={params.slug} />;
}
