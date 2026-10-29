import { SubmissionDetail } from "@/components/submissions/submission-views";

export default function SubmissionPage({ params }: { params: { id: string } }) {
  return <SubmissionDetail id={Number(params.id)} />;
}
