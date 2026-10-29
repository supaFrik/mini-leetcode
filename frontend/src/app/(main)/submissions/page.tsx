import { Suspense } from "react";
import { SubmissionsView } from "@/components/submissions/submission-views";

export default function SubmissionsPage() {
  return <Suspense fallback={null}><SubmissionsView /></Suspense>;
}
