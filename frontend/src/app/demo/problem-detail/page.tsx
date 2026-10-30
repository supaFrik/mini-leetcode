"use client";

import ProblemDetailDemo from "@/components/blocks/problem-detail-demo";

/**
 * Standalone demo of the `problem-detail` block.
 *
 * Public route (outside the `(main)` group) so it can be previewed without
 * authenticating.
 */
export default function ProblemDetailDemoPage() {
  return <ProblemDetailDemo />;
}
