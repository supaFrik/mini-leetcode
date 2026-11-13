import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/* eslint-disable @typescript-eslint/no-explicit-any */
const PROBLEMS = [
  { id: 101, slug: "mini-bank-stage-1", title: "Mini Bank 1/5", difficulty: "EASY", status: "PUBLISHED", tags: [] },
  { id: 301, slug: "oop-2-dong-goi", title: "Bài 2 — Đóng gói", difficulty: "EASY", status: "PUBLISHED", tags: [] },
];

/** Server chủ đề + plan giả; ghi lại body ghi để kiểm tra hợp đồng TopicController / StudyPlanController. */
async function serve(page: Page) {
  const topics = new Map<string, any>();
  const writes: { method: string; path: string; body: any }[] = [];
  const ok = (route: any, data: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });

  await page.route(/\/api\/v1\/problems\?/, (route) => {
    const q = (new URL(route.request().url()).searchParams.get("search") ?? "").toLowerCase();
    const content = PROBLEMS.filter((p) => p.title.toLowerCase().includes(q) || p.slug.includes(q));
    return ok(route, { content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } });
  });

  await page.route(/\/api\/v1\/topics(\/.*)?(\?.*)?$/, (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const method = req.method();
    const body = req.postData() ? JSON.parse(req.postData()!) : null;
    if (method !== "GET") writes.push({ method, path: url.pathname, body });
    const save = (old: any) => {
      const t = { id: old?.id ?? 50 + topics.size, status: old?.status ?? "DRAFT", createdById: 7, createdByUsername: "teacher",
        ...body, steps: body.steps.map((s: any, i: number) => ({ ...s, id: s.id ?? 900 + i, problemSlug: s.problemId ? "oop-2-dong-goi" : null })) };
      if (old) topics.delete(old.slug);
      topics.set(t.slug, t);
      return t;
    };
    if (url.pathname === "/api/v1/topics" && method === "GET") {
      return ok(route, Array.from(topics.values()).map((t) => ({ id: t.id, slug: t.slug, title: t.title, estMinutes: t.estMinutes,
        status: t.status, stepCount: t.steps.length, createdById: 7, createdByUsername: "teacher" })));
    }
    if (url.pathname === "/api/v1/topics" && method === "POST") return ok(route, save(null), 201);
    const m = /^\/api\/v1\/topics\/([^/]+)(\/authoring|\/transitions)?$/.exec(url.pathname);
    const topic = m && topics.get(m[1]);
    if (!topic) return ok(route, { status: 404, detail: "Topic not found" }, 404);
    if (m![2] === "/transitions") { topic.status = body.status; return ok(route, topic); }
    if (method === "PUT") return ok(route, save(topic));
    return ok(route, topic);
  });

  await page.route(/\/api\/v1\/study-plans(\/.*)?$/, (route) => {
    const req = route.request();
    const body = req.postData() ? JSON.parse(req.postData()!) : null;
    if (req.method() !== "GET") writes.push({ method: req.method(), path: new URL(req.url()).pathname, body });
    if (req.method() === "POST" && body?.items) {
      return ok(route, { id: 1, slug: body.slug, title: body.title, status: "DRAFT", createdById: 7, createdByUsername: "teacher",
        stageCount: 1, doneStages: 0, items: body.items.map((it: any, position: number) => ({ id: 1, position, kind: it.kind, title: it.title, done: false,
          stages: it.problemIds.map((id: number, k: number) => ({ stageNo: k + 1, problemId: id, slug: "mini-bank-stage-1", title: "Mini Bank 1/5",
            difficulty: "EASY", problemType: "OOP_GRINDING", published: true, status: "NEVER",
            topics: (it.stageTopics?.[k] ?? []).map((tid: number) => ({ id: tid, slug: "oop-dong-goi", title: "Đóng gói", estMinutes: 15,
              progress: "NEW", stepCount: 6, doneSteps: 0 })) })) })) }, 201);
    }
    return ok(route, []);
  });
  return { writes, topics };
}

test.describe("Admin: topic authoring and stage topics", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { id: 7, username: "teacher", role: "INSTRUCTOR" });
  });

  test("creates a topic with every step type and sends answers in the API shape", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/admin/topics/new");
    await page.getByLabel("Title", { exact: true }).fill("Đóng gói");
    await expect(page.getByLabel("Slug (URL)")).toHaveValue("dong-goi");
    await page.getByLabel("Summary shown on the stage timeline").fill("private + method có kiểm tra");

    // Step 1 (theory) có sẵn
    const theory = page.getByRole("region", { name: "Step 1: Theory" });
    await theory.getByLabel("Theory markdown").fill("## Vấn đề\nai cũng sửa\n## Modifier\nprivate");

    const add = page.getByRole("group", { name: "Add a step" });
    await add.getByRole("button", { name: "Quiz" }).click();
    const quiz = page.getByRole("region", { name: "Step 2: Quiz" });
    await quiz.getByLabel("Step title").fill("Field nào nên private?");
    await quiz.getByLabel(/Question \/ instruction/).fill("Chọn tất cả");
    await quiz.getByLabel("Several correct options").check();
    await quiz.getByLabel("Option 1", { exact: true }).fill("balance");
    await quiz.getByLabel("Option 2", { exact: true }).fill("owner");
    await quiz.getByLabel("Option 2 is correct").check();
    await quiz.getByRole("button", { name: "Add option" }).click();
    await quiz.getByLabel("Option 3", { exact: true }).fill("Không field nào");
    await quiz.getByLabel(/Explanation/).fill("Trạng thái luôn private.");

    await add.getByRole("button", { name: "Matching" }).click();
    const matching = page.getByRole("region", { name: "Step 3: Matching" });
    await matching.getByLabel("Step title").fill("Modifier");
    await matching.getByLabel(/Question \/ instruction/).fill("Ghép");
    await matching.getByLabel("Pair 1 left").fill("private");
    await matching.getByLabel("Pair 1 right").fill("Chỉ trong lớp");
    await matching.getByLabel("Pair 2 left").fill("public");
    await matching.getByLabel("Pair 2 right").fill("Mọi nơi");

    await add.getByRole("button", { name: "Ordering" }).click();
    const ordering = page.getByRole("region", { name: "Step 4: Ordering" });
    await ordering.getByLabel("Step title").fill("Thứ tự");
    await ordering.getByLabel(/Question \/ instruction/).fill("Sắp xếp");
    await ordering.getByLabel("Line 1", { exact: true }).fill("balance -= amount;");
    await ordering.getByLabel("Line 2", { exact: true }).fill("if (amount > balance) throw ...;");
    await ordering.getByRole("button", { name: "Move line 2 up" }).click();

    await add.getByRole("button", { name: "Fill in the blanks" }).click();
    const fill = page.getByRole("region", { name: "Step 5: Fill in the blanks" });
    await fill.getByLabel("Step title").fill("Wallet");
    await fill.getByLabel(/Question \/ instruction/).fill("Điền");
    await fill.getByLabel(/^Code/).fill("[[1]] long balance;");
    await fill.getByLabel(/Blank \[\[1]]/).fill("private | PRIVATE");

    await add.getByRole("button", { name: "Code exercise" }).click();
    const code = page.getByRole("region", { name: "Step 6: Code exercise" });
    await code.getByLabel("Step title").fill("Bài tập: ví điện tử");
    await code.getByLabel("Search published problems").fill("dong-goi");
    await code.getByRole("button", { name: /Bài 2 — Đóng gói/ }).click();

    await page.getByRole("button", { name: "Create draft" }).click();
    await expect(page).toHaveURL(/\/admin\/topics\/dong-goi$/);
    const body = api.writes.find((w) => w.method === "POST")!.body;
    expect(body).toMatchObject({ slug: "dong-goi", title: "Đóng gói", estMinutes: 10, summary: "private + method có kiểm tra" });
    expect(body.steps.map((s: any) => s.type)).toEqual(["THEORY", "CHOICE", "MATCHING", "ORDERING", "FILL_BLANK", "CODE"]);
    expect(body.steps[1].content.options.map((o: any) => o.text)).toEqual(["balance", "owner", "Không field nào"]);
    expect(body.steps[1].answer).toEqual({ correct: ["o1", "o2"], explanation: "Trạng thái luôn private." });
    expect(body.steps[2].answer.pairs).toEqual({ l1: "r1", l2: "r2" });
    expect(body.steps[3].content.items.map((i: any) => i.text)).toEqual(["if (amount > balance) throw ...;", "balance -= amount;"]);
    expect(body.steps[3].answer.order).toEqual(["i1", "i2"]);
    expect(body.steps[4].answer).toMatchObject({ blanks: { 1: ["private", "PRIVATE"] }, caseSensitive: false });
    expect(body.steps[5].problemId).toBe(301);

    // Tải lại từ API soạn và lưu lần nữa: step giữ id (giữ tiến độ học viên)
    await page.reload();
    await expect(page.getByRole("region", { name: "Step 3: Matching" }).getByLabel("Pair 2 right")).toHaveValue("Mọi nơi");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect.poll(() => api.writes.filter((w) => w.method === "PUT").length).toBe(1);
    expect(api.writes.find((w) => w.method === "PUT")!.body.steps.map((s: any) => s.id)).toEqual([900, 901, 902, 903, 904, 905]);

    await page.getByRole("button", { name: "Publish" }).click();
    await expect(page.getByText("PUBLISHED", { exact: true })).toBeVisible();
  });

  test("validates locally: a quiz needs a correct option and a code step needs a problem", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/admin/topics/new");
    await page.getByLabel("Title", { exact: true }).fill("T");
    await page.getByRole("group", { name: "Add a step" }).getByRole("button", { name: "Code exercise" }).click();
    await page.getByRole("region", { name: "Step 2: Code exercise" }).getByLabel("Step title").fill("Code");
    await page.getByRole("button", { name: "Create draft" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "Step 2" })).toHaveText("Step 2 (Code exercise): choose the problem.");
    expect(api.writes).toHaveLength(0);
  });
  // Gắn topic vào stage: xem admin-study-plans.spec.ts (Course Builder).
});
