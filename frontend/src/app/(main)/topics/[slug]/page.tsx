import { TopicView } from "@/components/topics/topic-view";

// Next 14: params / searchParams là object thường. ?plan=&stage= chỉ dùng cho breadcrumb và nút quay lại.
export default function TopicPage({ params, searchParams }: {
  params: { slug: string }; searchParams: { plan?: string; stage?: string };
}) {
  const stage = Number(searchParams.stage);
  return <TopicView slug={params.slug} planSlug={searchParams.plan} stageNo={Number.isFinite(stage) && stage > 0 ? stage : undefined} />;
}
