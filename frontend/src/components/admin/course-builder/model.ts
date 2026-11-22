/**
 * Mô hình dữ liệu của Course Builder: plan → session → mục (lesson = topic, problem / stage = đề).
 * Nút trên outline được định danh bằng chuỗi để giữ trong URL (?node=...).
 */
import type {
  Difficulty, ProblemLifecycle, StudyPlanDetail, StudyPlanDraft, StudyPlanEntryType, StudyPlanStatus,
} from "@/types/api";

export interface TopicNode { id: number; slug: string; title: string; status?: StudyPlanStatus; stepCount?: number }
export interface StageNode {
  problemId: number; slug: string | null; title: string | null; difficulty: Difficulty | null;
  status: ProblemLifecycle | null; learners: number;
}
export interface EntryNode { key: string; type: StudyPlanEntryType; topic?: TopicNode; problem?: StageNode }
export interface SessionNode { key: string; title: string; description: string; entries: EntryNode[] }
export interface PlanModel { slug: string; title: string; description: string; status: StudyPlanStatus; sessions: SessionNode[] }

export type AddMode = "lesson" | "problem";
/** "none" | "session:<key>" | "entry:<key>" | "add:<sessionKey>:<mode>" */
export type NodeId = string;

let keySeq = 0;
export const newKey = () => `n${Date.now().toString(36)}${(keySeq++).toString(36)}`;

export function fromDetail(plan: StudyPlanDetail): PlanModel {
  return {
    slug: plan.slug, title: plan.title, description: plan.description ?? "", status: plan.status,
    sessions: plan.items.map((it) => ({
      key: newKey(), title: it.title, description: it.description ?? "",
      entries: it.entries.flatMap((e): EntryNode[] => {
        if (e.type === "LESSON") {
          const t = e.topic;
          return t ? [{ key: newKey(), type: e.type, topic: { id: t.id, slug: t.slug, title: t.title, status: t.status, stepCount: t.stepCount } }] : [];
        }
        const st = e.stageNo ? it.stages[e.stageNo - 1] : undefined;
        return st ? [{ key: newKey(), type: e.type, problem: {
          problemId: st.problemId, slug: st.slug, title: st.title, difficulty: st.difficulty,
          status: st.problemStatus ?? (st.published ? "PUBLISHED" : "DRAFT"), learners: st.learnersOnStage ?? 0,
        } }] : [];
      }),
    })),
  };
}

/** Không gửi slug: server tự đặt theo title. */
export function toDraft(m: PlanModel, expectedUpdatedAt?: string | null): StudyPlanDraft {
  return {
    title: m.title.trim() || m.slug, description: m.description.trim() || undefined, expectedUpdatedAt,
    sessions: m.sessions.map((s, i) => ({
      title: s.title.trim() || `Session ${i + 1}`, description: s.description.trim() || undefined,
      entries: s.entries.map((e) => (e.topic ? { type: e.type, topicId: e.topic.id } : { type: e.type, problemId: e.problem!.problemId })),
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

export function findEntry(m: PlanModel, key: string): { session: SessionNode; index: number; entry: EntryNode } | null {
  for (const session of m.sessions) {
    const index = session.entries.findIndex((e) => e.key === key);
    if (index >= 0) return { session, index, entry: session.entries[index] };
  }
  return null;
}

export function parseNode(id: string | null): { kind: "none" } | { kind: "session"; key: string }
  | { kind: "entry"; key: string } | { kind: "add"; key: string; mode: AddMode } {
  const [kind, key, mode] = (id ?? "").split(":");
  if (kind === "session" || kind === "entry") return { kind, key };
  if (kind === "add") return { kind, key, mode: mode as AddMode };
  return { kind: "none" };
}

/** Di chuyển nút đang chọn lên / xuống trong cùng cấp (Alt+↑ / Alt+↓). */
export function shift(m: PlanModel, node: NodeId, delta: -1 | 1): PlanModel {
  const n = parseNode(node);
  if (n.kind === "session") {
    const i = m.sessions.findIndex((s) => s.key === n.key);
    return { ...m, sessions: move(m.sessions, i, i + delta) };
  }
  if (n.kind === "entry") {
    return { ...m, sessions: m.sessions.map((s) => {
      const k = s.entries.findIndex((e) => e.key === n.key);
      return k < 0 ? s : { ...s, entries: move(s.entries, k, k + delta) };
    }) };
  }
  return m;
}

export const STATUS_MARK: Record<string, string> = { PUBLISHED: "✓", DRAFT: "●", REVIEWING: "●", ARCHIVED: "○" };
