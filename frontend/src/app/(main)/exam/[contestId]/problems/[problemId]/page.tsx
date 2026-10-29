import { ExamLoader } from "@/components/workspace/workspace-loaders";

export default function ExamProblemPage({ params }: { params: { contestId: string; problemId: string } }) {
  return <div className="h-full"><ExamLoader contestId={Number(params.contestId)} problemId={params.problemId} /></div>;
}
