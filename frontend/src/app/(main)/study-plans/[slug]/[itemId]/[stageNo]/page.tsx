import { notFound } from "next/navigation";
import { StageView } from "@/components/study-plans/stage-view";

export default function StagePage({ params }: { params: { slug: string; itemId: string; stageNo: string } }) {
  const itemId = Number(params.itemId);
  const stageNo = Number(params.stageNo);
  if (!Number.isInteger(itemId) || !Number.isInteger(stageNo) || stageNo < 1) notFound();
  return <StageView slug={params.slug} itemId={itemId} stageNo={stageNo} />;
}
