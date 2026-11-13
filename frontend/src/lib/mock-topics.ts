/**
 * Chủ đề học ở chế độ mock (NEXT_PUBLIC_USE_MOCK=true): dữ liệu trong bộ nhớ + bộ chấm giống
 * StepGrader ở server (specs/topics.spec.md). Đáp án chỉ nằm ở đây, không bao giờ trả qua getTopic().
 */
import { ApiError } from "./api";
import { decodeUser, getToken } from "./auth";
import type {
  AttemptResult, AuthoringStep, AuthoringTopic, IdText, LearnerStep, LearnerTopic, StepAnswer, StepContent,
  StudyPlanStatus, TopicDraft, TopicProgress, TopicRef, TopicSummary,
} from "@/types/api";

const sleep = (ms = 120) => new Promise<void>((r) => setTimeout(r, ms));
const notFound = (detail: string) => new ApiError(404, { title: "Resource Not Found", status: 404, detail });
const badRequest = (detail: string) => new ApiError(400, { title: "Bad Request", status: 400, detail });

let nextStepId = 1000;
const step = (s: Omit<AuthoringStep, "id">): AuthoringStep => ({ ...s, id: nextStepId++ });

const TOPICS: AuthoringTopic[] = [
  {
    id: 1, slug: "oop-dong-goi", title: "Đóng gói (Encapsulation)", estMinutes: 15, status: "PUBLISHED",
    summary: "Giấu trạng thái bằng private, chỉ cho đổi qua method có kiểm tra.", createdById: 2, createdByUsername: "instructor",
    steps: [
      step({ type: "THEORY", title: "Lý thuyết", content: { markdown:
        "## Vấn đề: ai cũng sửa được số dư\nField `public` cho phép gán `account.balance = -1000000`.\n\n"
        + "## Access modifier\n| Modifier | Phạm vi |\n|---|---|\n| `private` | Trong lớp |\n| `public` | Mọi nơi |\n\n"
        + "## Method có kiểm tra\n```java\npublic void deposit(long amount) {\n    if (amount <= 0) throw new IllegalArgumentException(\"Số tiền phải dương\");\n    balance += amount;\n}\n```" } }),
      step({ type: "CHOICE", title: "Field nào nên private?", content: { prompt: "Chọn **tất cả** field nên để `private` trong `Account`.", multiple: true,
        hint: "Trạng thái thay đổi theo nghiệp vụ luôn được giấu.",
        options: [{ id: "a", text: "balance" }, { id: "b", text: "owner" }, { id: "c", text: "Không field nào, để public cho tiện" }] },
        answer: { correct: ["a", "b"], explanation: "Mọi field trạng thái đều private; thay đổi đi qua deposit / withdraw." } }),
      step({ type: "MATCHING", title: "Modifier và phạm vi", content: { prompt: "Ghép mỗi modifier với phạm vi truy cập của nó.",
        left: [{ id: "l1", text: "private" }, { id: "l2", text: "(mặc định)" }, { id: "l3", text: "protected" }, { id: "l4", text: "public" }],
        right: [{ id: "r1", text: "Chỉ trong lớp" }, { id: "r2", text: "Cùng package" }, { id: "r3", text: "Cùng package + lớp con" }, { id: "r4", text: "Mọi nơi" }] },
        answer: { pairs: { l1: "r1", l2: "r2", l3: "r3", l4: "r4" } } }),
      step({ type: "ORDERING", title: "Thứ tự trong withdraw", content: { prompt: "Sắp xếp các dòng của `withdraw` theo đúng thứ tự.",
        items: [{ id: "a", text: "public void withdraw(long amount) {" }, { id: "b", text: "    if (amount > balance) throw new IllegalArgumentException(\"Không đủ tiền\");" },
          { id: "c", text: "    balance -= amount;" }, { id: "d", text: "}" }] },
        answer: { order: ["a", "b", "c", "d"], explanation: "Kiểm tra trước, đổi trạng thái sau." } }),
      step({ type: "FILL_BLANK", title: "Hoàn thành lớp Wallet", content: { prompt: "Điền từ khoá còn thiếu.",
        code: "public class Wallet {\n    [[1]] long balance;\n\n    public long [[2]]() {\n        return balance;\n    }\n}" },
        answer: { blanks: { 1: ["private"], 2: ["getBalance"] }, caseSensitive: true } }),
      step({ type: "CODE", title: "Bài tập: ví điện tử", content: { intro: "Áp dụng: viết lớp Wallet đóng gói số dư." },
        problemId: 13, problemSlug: "bank-account-class" }),
    ],
  },
  {
    id: 2, slug: "oop-ke-thua", title: "Kế thừa & ghi đè", estMinutes: 12, status: "PUBLISHED",
    summary: "extends, super(...), @Override.", createdById: 2, createdByUsername: "instructor",
    steps: [
      step({ type: "THEORY", title: "Lý thuyết", content: { markdown: "## Quan hệ is-a\n`Manager extends Employee`.\n\n## super(...)\nConstructor lớp con gọi lớp cha trước tiên." } }),
      step({ type: "CHOICE", title: "Câu nào là quan hệ is-a?", content: { prompt: "Chọn quan hệ phù hợp với kế thừa.",
        options: [{ id: "a", text: "SavingsAccount là một Account" }, { id: "b", text: "Bank có danh sách Account" }] },
        answer: { correct: ["a"] } }),
    ],
  },
];
/** Chủ đề gắn vào stage của project Mini Bank trong plan mock: stage 1 → [1], stage 2 → [2]. */
export const MOCK_STAGE_TOPICS: Record<number, number[]> = { 1: [1], 2: [2] };

const progress = new Map<number, { passed: boolean; attempts: number }>();
const skipped = new Set<number>();

const findTopic = (slug: string) => {
  const t = TOPICS.find((x) => x.slug === slug);
  if (!t) throw notFound(`Topic not found: ${slug}`);
  return t;
};
const passed = (s: AuthoringStep) => (s.type === "CODE" ? false : !!progress.get(s.id ?? -1)?.passed);
const doneOf = (t: AuthoringTopic) => t.steps.filter(passed).length;
function statusOf(t: AuthoringTopic): TopicProgress {
  const done = doneOf(t);
  if (t.steps.length > 0 && done === t.steps.length) return "DONE";
  if (skipped.has(t.id)) return "SKIPPED";
  return done > 0 || t.steps.some((s) => (progress.get(s.id ?? -1)?.attempts ?? 0) > 0) ? "IN_PROGRESS" : "NEW";
}

/** Xáo trộn ổn định theo id (giống server) để thứ tự nhập không lộ đáp án. */
function shuffle(items: IdText[], seed: number): IdText[] {
  const out = [...items];
  let x = seed * 9301 + 49297;
  for (let i = out.length - 1; i > 0; i--) {
    x = (x * 9301 + 49297) % 233280;
    const j = Math.floor((x / 233280) * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  if (out.length > 1 && out.every((it, i) => it.id === items[i].id)) out.push(out.shift()!);
  return out;
}

function publicContent(s: AuthoringStep): StepContent {
  const c = s.content as Record<string, unknown>;
  switch (s.type) {
    case "MATCHING": return { ...c, right: shuffle(c.right as IdText[], s.id ?? 0) } as StepContent;
    case "ORDERING": return { ...c, items: shuffle(c.items as IdText[], s.id ?? 0) } as StepContent;
    case "FILL_BLANK": return { ...c, blanks: Array.from(String(c.code).matchAll(/\[\[([A-Za-z0-9_-]+)]]/g)).map((m) => m[1]) } as StepContent;
    case "CODE": return { ...c, problemId: s.problemId ?? undefined, problemSlug: s.problemSlug ?? undefined,
      problemTitle: s.problemSlug ?? undefined, difficulty: "EASY", published: true } as StepContent;
    default: return c as StepContent;
  }
}

export async function getTopic(slug: string): Promise<LearnerTopic> {
  await sleep();
  const t = findTopic(slug);
  const steps: LearnerStep[] = t.steps.map((s, i) => ({
    id: s.id ?? i, position: i + 1, type: s.type, title: s.title, content: publicContent(s),
    passed: passed(s), attempts: progress.get(s.id ?? -1)?.attempts ?? 0,
  }));
  return { id: t.id, slug: t.slug, title: t.title, summary: t.summary, estMinutes: t.estMinutes, status: t.status,
    progress: statusOf(t), stepCount: steps.length, doneSteps: doneOf(t), steps };
}

function grade(s: AuthoringStep, a: StepAnswer): { correct: boolean; message: string } {
  const ans = (s.answer ?? {}) as Record<string, unknown>;
  const given = a as Record<string, unknown>;
  switch (s.type) {
    case "THEORY": return { correct: true, message: "Marked as read" };
    case "CHOICE": {
      const want = ans.correct as string[];
      const got = (given.selected as string[] | undefined) ?? [];
      const ok = want.length === got.length && want.every((x) => got.includes(x));
      return { correct: ok, message: ok ? "Correct!" : want.length > 1 ? `Not quite. This question has ${want.length} correct options.` : "Not quite. Try again." };
    }
    case "MATCHING": {
      const want = ans.pairs as Record<string, string>;
      const got = (given.pairs as Record<string, string> | undefined) ?? {};
      const right = Object.keys(want).filter((k) => want[k] === got[k]).length;
      const total = Object.keys(want).length;
      return { correct: right === total, message: right === total ? "Correct!" : `${right} of ${total} pairs are correct.` };
    }
    case "ORDERING": {
      const want = ans.order as string[];
      const got = (given.order as string[] | undefined) ?? [];
      const right = want.filter((id, i) => got[i] === id).length;
      return { correct: right === want.length, message: right === want.length ? "Correct!" : `${right} of ${want.length} lines are in the right place.` };
    }
    case "FILL_BLANK": {
      const blanks = ans.blanks as Record<string, string[]>;
      const cs = !!ans.caseSensitive;
      const norm = (v: string) => { const t = v.trim().replace(/\s+/g, " "); return cs ? t : t.toLowerCase(); };
      const values = (given.values as Record<string, string> | undefined) ?? {};
      const ids = Array.from(String((s.content as Record<string, unknown>).code).matchAll(/\[\[([A-Za-z0-9_-]+)]]/g)).map((m) => m[1]);
      const wrong = ids.map((id, i) => ((blanks[id] ?? []).some((x) => norm(x) === norm(values[id] ?? "")) ? 0 : i + 1)).filter(Boolean);
      return { correct: wrong.length === 0, message: wrong.length === 0 ? "Correct!"
        : wrong.length === 1 ? `Blank ${wrong[0]} is not right yet.` : `Blanks ${wrong.slice(0, -1).join(", ")} and ${wrong[wrong.length - 1]} are not right yet.` };
    }
    default: throw badRequest("A code step is completed by an accepted submission to its problem");
  }
}

export async function attemptTopicStep(slug: string, stepId: number, answer: StepAnswer): Promise<AttemptResult> {
  await sleep();
  const t = findTopic(slug);
  const s = t.steps.find((x) => x.id === stepId);
  if (!s) throw notFound(`Step not found: ${stepId}`);
  const g = grade(s, answer);
  const p = progress.get(stepId) ?? { passed: false, attempts: 0 };
  p.attempts += 1;
  p.passed = p.passed || g.correct;
  progress.set(stepId, p);
  const explanation = g.correct ? ((s.answer as Record<string, unknown> | undefined)?.explanation as string | undefined) ?? null : null;
  return { correct: g.correct, message: g.message, explanation, stepPassed: p.passed, attempts: p.attempts,
    progress: statusOf(t), doneSteps: doneOf(t), stepCount: t.steps.length };
}

export async function setTopicSkipped(slug: string, value: boolean): Promise<void> {
  await sleep();
  const t = findTopic(slug);
  if (value) skipped.add(t.id); else skipped.delete(t.id);
}

/** Chủ đề (kèm tiến độ) cho một stage của plan mock. */
export function stageTopicRefs(ids: number[]): TopicRef[] {
  return ids.map((id) => TOPICS.find((t) => t.id === id)).filter((t): t is AuthoringTopic => !!t && t.status === "PUBLISHED")
    .map((t) => ({ id: t.id, slug: t.slug, title: t.title, summary: t.summary, estMinutes: t.estMinutes,
      progress: statusOf(t), stepCount: t.steps.length, doneSteps: doneOf(t) }));
}

// ── Soạn ────────────────────────────────────────────────────────────────────

export async function listTopics(search = ""): Promise<TopicSummary[]> {
  await sleep();
  const q = search.trim().toLowerCase();
  return TOPICS.filter((t) => !q || t.title.toLowerCase().includes(q) || t.slug.includes(q)).map((t) => ({
    id: t.id, slug: t.slug, title: t.title, summary: t.summary, estMinutes: t.estMinutes, status: t.status,
    stepCount: t.steps.length, createdById: t.createdById, createdByUsername: t.createdByUsername,
  }));
}
export async function getTopicForAuthoring(slug: string): Promise<AuthoringTopic> {
  await sleep();
  return structuredClone(findTopic(slug));
}
export async function saveTopic(slug: string | null, draft: TopicDraft): Promise<AuthoringTopic> {
  await sleep();
  const user = decodeUser(getToken());
  const old = slug ? findTopic(slug) : null;
  if (TOPICS.some((t) => t.slug === draft.slug && t !== old)) {
    throw new ApiError(409, { title: "Resource Conflict", status: 409, detail: `Topic slug already exists: ${draft.slug}` });
  }
  const topic: AuthoringTopic = {
    id: old?.id ?? Date.now(), slug: draft.slug, title: draft.title, summary: draft.summary ?? null, estMinutes: draft.estMinutes,
    status: old?.status ?? "DRAFT", createdById: old?.createdById ?? user?.id ?? 1, createdByUsername: old?.createdByUsername ?? user?.username ?? "me",
    steps: draft.steps.map((s) => ({ ...s, id: s.id ?? nextStepId++ })),
  };
  if (old) TOPICS.splice(TOPICS.indexOf(old), 1, topic); else TOPICS.push(topic);
  return structuredClone(topic);
}
export async function transitionTopic(slug: string, status: StudyPlanStatus): Promise<AuthoringTopic> {
  await sleep();
  const t = findTopic(slug);
  t.status = status;
  return structuredClone(t);
}
export async function deleteTopic(slug: string): Promise<void> {
  await sleep();
  TOPICS.splice(TOPICS.indexOf(findTopic(slug)), 1);
}
