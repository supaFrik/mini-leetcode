import { Suspense } from "react";
import { ProblemsView } from "@/components/problems/problems-view";

export default function ProblemsPage() {
  return (
    <Suspense fallback={null}>
      <ProblemsView />
    </Suspense>
  );
}