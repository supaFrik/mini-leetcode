import { ContestOverview } from "@/components/contests/contest-views";

export default function ContestPage({ params }: { params: { contestId: string } }) {
  return <ContestOverview contestId={Number(params.contestId)} />;
}
