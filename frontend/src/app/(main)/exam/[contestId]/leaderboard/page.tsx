import { LeaderboardView } from "@/components/contests/contest-views";

export default function LeaderboardPage({ params }: { params: { contestId: string } }) {
  return <LeaderboardView contestId={Number(params.contestId)} />;
}
