import { Suspense } from "react";
import { SubmissionsList } from "@/components/submissions/submissions-list";

export default function SubmissionsPage() {
  return <Suspense fallback={null}><SubmissionsList /></Suspense>;
}
