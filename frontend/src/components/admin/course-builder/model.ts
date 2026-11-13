/**
 * Mô hình dữ liệu của Course Builder (specs/course-builder.spec.md): plan → item → stage (đề) → topic.
 * Nút trên outline được định danh bằng chuỗi để giữ trong URL (?node=...).
 */
import type {
  Difficulty, ProblemLifecycle, StudyPlanDetail, StudyPlanDraft, StudyPlanItemKind, StudyPlanStatus,
} from "@/types/api";

export interface TopicNode { id: number; slug: string; title: string; status?: StudyPlanStatus }
export interface StageNode {
  problemId: number; slug: string | null; title: string | null; difficulty: Difficulty | null;
  status: ProblemLifecycle | null; learners: number; topics: TopicNode[];
}
export interface ItemNode { key: string; kind: StudyPlanItemKind; title: string; description: string; stages: StageNode[] }
export interface PlanModel { slug: string; title: string; description: string; status: StudyPlanStatus; items: ItemNode[] }

/** "plan" | "item:<key>" | "stage:<problemId>" | "topic:<topicId>@<problemId>" */
export type NodeId = string;

let keySeq = 0;
export const newKey = () => `n${Date.now().toString(36)}${(keySeq++).toString(36)}`;

export function fromDetail(plan: StudyPlanDetail): PlanModel {
  return {
    slug: plan.slug, title: plan.title, description: plan.description ?? "", status: plan.status,
    items: plan.items.map((it) => ({
      key: newKey(), kind: it.kind, title: it.title, description: it.description ?? "",
      stages: it.stages.map((st) => ({
        problemId: st.problemId, slug: st.slug, title: st.title, difficulty: st.difficulty,
        status: st.problemStatus ?? (st.published ? "PUBLISHED" : "DRAFT"), learners: st.learnersOnStage ?? 0,
        topics: (st.topics ?? []).map((t) => ({ id: t.id, slug: t.slug, title: t.title, status: t.status })),
      })),
    })),
  };
}

export function toDraft(m: PlanModel, expectedUpdatedAt?: string | null): StudyPlanDraft {
  return {
    slug: m.slug, title: m.title.trim() || "Untitled plan", description: m.description.trim() || undefined, expectedUpdatedAt,
    items: m.items.map((it) => ({
      kind: it.kind, title: it.title.trim() || (it.kind === "PROJECT" ? "Untitled project" : "Untitled practice"),
      description: it.description.trim() || undefined,
      problemIds: it.stages.map((s) => s.problemId),
      stageTopics: it.stages.map((s) => s.topics.map((t) => t.id)),
    })),
  };
}

/** Chữ ký nội dung plan (bỏ trạng thái đề / topic) — so để biết có thay đổi chưa lưu. */
export const signature = (m: PlanModel) => JSON.stringify(toDraft(m));

export function move<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length || from === to) return list;
  const copy = [...list];
  const [x] = copy.splice(from, 1);
  copy.splice(to, 0, x);
  return copy;
}

export function findStage(m: PlanModel, problemId: number): { item: ItemNode; index: number; stage: StageNode } | null {
  for (const item of m.items) {
    const index = item.stages.findIndex((s) => s.problemId === problemId);
    if (index >= 0) return { item, index, stage: item.stages[index] };
  }
  return null;
}

export function parseNode(id: string | null): { kind: "plan" } | { kind: "item"; key: string }
  | { kind: "stage"; problemId: number } | { kind: "topic"; topicId: number; problemId: number } {
  if (!id || id === "plan") return { kind: "plan" };
  const [kind, rest] = id.split(":");
  if (kind === "item") return { kind: "item", key: rest };
  if (kind === "stage") return { kind: "stage", problemId: Number(rest) };
  if (kind === "topic") {
    const [topicId, problemId] = rest.split("@").map(Number);
    return { kind: "topic", topicId, problemId };
  }
  return { kind: "plan" };
}

/** Di chuyển nút đang chọn lên / xuống trong cùng cấp (Alt+↑ / Alt+↓). */
export function shift(m: PlanModel, node: NodeId, delta: -1 | 1): PlanModel {
  const n = parseNode(node);
  if (n.kind === "item") {
    const i = m.items.findIndex((it) => it.key === n.key);
    return { ...m, items: move(m.items, i, i + delta) };
  }
  if (n.kind === "stage" || n.kind === "topic") {
    return {
      ...m,
      items: m.items.map((it) => {
        const k = it.stages.findIndex((s) => s.problemId === n.problemId);
        if (k < 0) return it;
        if (n.kind === "stage") return { ...it, stages: move(it.stages, k, k + delta) };
        const st = it.stages[k];
        const t = st.topics.findIndex((x) => x.id === n.topicId);
        return { ...it, stages: it.stages.map((s, j) => (j === k ? { ...s, topics: move(st.topics, t, t + delta) } : s)) };
      }),
    };
  }
  return m;
}

export const STATUS_MARK: Record<string, string> = { PUBLISHED: "✓", DRAFT: "●", REVIEWING: "●", ARCHIVED: "○" };
