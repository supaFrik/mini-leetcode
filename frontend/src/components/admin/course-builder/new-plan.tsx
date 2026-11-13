"use client";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { errorMessage } from "@/lib/api";
import { createStudyPlan } from "@/lib/services";

/** /admin/study-plans/new: tạo plan nháp trống rồi chuyển sang Course Builder (slug đổi theo tiêu đề khi soạn). */
export function NewStudyPlan() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const slug = `untitled-${Date.now().toString(36)}`;
    createStudyPlan({ slug, title: "Untitled plan", items: [] })
      .then((p) => router.replace(`/admin/study-plans/${p.slug}?node=plan`))
      .catch((e) => setError(errorMessage(e)));
  }, [router]);
  return error ? <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{error}</div>
    : <div className="mx-auto max-w-container px-4 py-6 text-mute">Creating a draft plan…</div>;
}
