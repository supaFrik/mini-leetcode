import { Suspense } from "react";
import { PracticeLoader } from "@/components/workspace/workspace-loaders";

// Next 14: params là object thường (từ Next 15 mới là Promise)
export default function ProblemPage({ params }: { params: { slug: string } }) {
  return <Suspense fallback={null}><div className="h-full"><PracticeLoader slug={params.slug} /></div></Suspense>;
}
