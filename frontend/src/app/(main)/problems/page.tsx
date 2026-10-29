import { Suspense } from "react";
import { ProblemsView } from "@/components/problems/problems-view";

// Suspense là bắt buộc khi component con dùng useSearchParams()
export default function ProblemsPage() {
  return <Suspense fallback={null}><ProblemsView /></Suspense>;
}
