import { api, USE_MOCK } from "./api";
import type { Page, ProblemSummary } from "@/types/api";

export interface ProblemQuery { page: number; q: string; difficulty: string; status: string; tags: string[] }
const PAGE_SIZE = 8;

const MOCK: ProblemSummary[] = Array.from({ length: 36 }, (_, i) => ({
  id: i + 1, slug: `problem-${i + 1}`,
  title: i === 0 ? "Two Sum" : `Placeholder #${i}`,
  difficulty: (["EASY", "MEDIUM", "HARD"] as const)[i % 3],
  tags: [["Array", "Hash Table"], ["Two Pointers", "Sorting"], ["Math", "Dynamic Programming"]][i % 3],
  status: (["DONE", "IN_PROGRESS", "NEVER"] as const)[i % 3],
}));

export async function fetchProblems(p: ProblemQuery): Promise<Page<ProblemSummary>> {
  if (USE_MOCK) {
    const all = MOCK.filter((x) =>
      (!p.q || x.title.toLowerCase().includes(p.q.toLowerCase())) &&
      (!p.difficulty || x.difficulty === p.difficulty) &&
      (!p.status || x.status === p.status) &&
      p.tags.every((t) => x.tags.includes(t)));
    const start = (p.page - 1) * PAGE_SIZE;
    return { content: all.slice(start, start + PAGE_SIZE), totalElements: all.length,
             totalPages: Math.max(1, Math.ceil(all.length / PAGE_SIZE)), number: p.page - 1, size: PAGE_SIZE };
  }
  const qs = new URLSearchParams({ page: String(p.page - 1), size: String(PAGE_SIZE) }); // backend đếm trang từ 0, UI từ 1
  if (p.q) qs.set("search", p.q);
  if (p.difficulty) qs.set("difficulty", p.difficulty);
  return api(`/api/v1/problems?${qs}`);
}