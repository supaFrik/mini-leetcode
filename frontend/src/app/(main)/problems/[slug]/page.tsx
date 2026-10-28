import { Workspace } from "@/components/workspace/workspace";

export default function ProblemPage({ params }: { params: { slug: string } }) {
  // TODO: GET /api/v1/problems/{idOrSlug}. Tạm dùng đề giả.
  const problem = {
    id: 1, title: "Two Sum", difficulty: "EASY" as const,
    descriptionMd: "Cho hai số nguyên `a` và `b` ở dòng đầu, in ra `a + b`.\n\n**Ví dụ**\n\n```\nInput: 1 2\nOutput: 3\n```",
  };
  return <Workspace problem={problem} mode="student" />;
}