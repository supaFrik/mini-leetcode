import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/** Hợp đồng y như TopicController / StudyPlanController (JSON trần). Đáp án chỉ nằm trong ANSWERS (phía "server"). */
const TOPIC = {
  id: 7, slug: "oop-dong-goi", title: "Đóng gói", summary: "private + method có kiểm tra", estMinutes: 15,
  status: "PUBLISHED", progress: "NEW", stepCount: 5, doneSteps: 0,
  steps: [
    { id: 1, position: 1, type: "THEORY", title: "Lý thuyết", passed: false, attempts: 0,
      content: { markdown: "## Vấn đề\nField public ai cũng sửa.\n## Modifier\nprivate là trong lớp.\n## Kiểm tra\nKiểm tra trước, đổi sau." } },
    { id: 2, position: 2, type: "CHOICE", title: "Field nào nên private?", passed: false, attempts: 0,
      content: { prompt: "Chọn tất cả", multiple: true, hint: "Trạng thái luôn giấu.",
        options: [{ id: "a", text: "balance" }, { id: "b", text: "owner" }, { id: "c", text: "Không field nào" }] } },
    { id: 3, position: 3, type: "ORDERING", title: "Thứ tự withdraw", passed: false, attempts: 0,
      content: { prompt: "Sắp xếp", items: [{ id: "y", text: "balance -= amount;" }, { id: "x", text: "if (amount > balance) throw ...;" }] } },
    { id: 4, position: 4, type: "MATCHING", title: "Modifier", passed: false, attempts: 0,
      content: { prompt: "Ghép", left: [{ id: "l1", text: "private" }, { id: "l2", text: "public" }],
        right: [{ id: "r2", text: "Mọi nơi" }, { id: "r1", text: "Chỉ trong lớp" }] } },
    { id: 5, position: 5, type: "FILL_BLANK", title: "Wallet", passed: false, attempts: 0,
      content: { prompt: "Điền", code: "[[1]] long balance;", blanks: ["1"] } },
  ],
};
const ANSWERS: Record<number, (a: Record<string, unknown>) => boolean> = {
  1: () => true,
  2: (a) => JSON.stringify([...(a.selected as string[])].sort()) === JSON.stringify(["a", "b"]),
  3: (a) => JSON.stringify(a.order) === JSON.stringify(["x", "y"]),
  4: (a) => JSON.stringify(a.pairs) === JSON.stringify({ l1: "r1", l2: "r2" }),
  5: (a) => (a.values as Record<string, string>)["1"]?.trim() === "private",
};

const stage = (n: number, status: string, topics: unknown[] = []) => ({
  stageNo: n, problemId: 100 + n, slug: `mini-bank-stage-${n}`, title: `Mini Bank ${n}/2 — Stage ${n}`,
  difficulty: "EASY", problemType: "OOP_GRINDING", published: true, status, topics,
});

async function serve(page: Page) {
  const passed = new Set<number>();
  const attempts: Array<{ stepId: number; answer: unknown }> = [];
  let skipped = false;
  // Khoá luyện tập giống TopicService: 3 lần sai liên tiếp ⇒ 5 phút + phải đọc lại lý thuyết.
  const streak = new Map<number, number>();
  let lock: { until: number; mustReread: boolean } | null = null;
  const lockFields = () => ({
    lockedUntil: lock && lock.until > Date.now() ? new Date(lock.until).toISOString() : null, mustReread: !!lock?.mustReread,
  });
  const progressOf = () => passed.size === TOPIC.steps.length ? "DONE" : skipped ? "SKIPPED" : passed.size || attempts.length ? "IN_PROGRESS" : "NEW";
  const ref = () => ({ id: 7, slug: TOPIC.slug, title: TOPIC.title, summary: TOPIC.summary, estMinutes: 15,
    progress: progressOf(), stepCount: 5, doneSteps: passed.size });
  const json = (body: unknown) => ({ contentType: "application/json", body: JSON.stringify(body) });

  await page.route(/\/api\/v1\/study-plans\/oop$/, (route) => route.fulfill(json({
    id: 1, slug: "oop", title: "OOP", status: "PUBLISHED", stageCount: 2, doneStages: 0,
    items: [{ id: 1, position: 0, kind: "PROJECT", title: "Mini Bank", done: false,
      stages: [stage(1, "NEVER", [ref(), { ...ref(), id: 8, slug: "oop-ke-thua", title: "Kế thừa", progress: "NEW", doneSteps: 0 }]),
        stage(2, "LOCKED")] }],
  })));
  await page.route(/\/api\/v1\/topics\/oop-dong-goi(\/.*)?$/, async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    if (path.endsWith("/skip")) {
      skipped = req.method() === "POST";
      return route.fulfill({ status: 204, body: "" });
    }
    const m = /\/steps\/(\d+)\/attempts$/.exec(path);
    if (m) {
      const stepId = Number(m[1]);
      const answer = (req.postDataJSON() as { answer: Record<string, unknown> }).answer;
      if (stepId !== 1 && lock && (lock.mustReread || lock.until > Date.now())) {
        return route.fulfill({ status: 409, ...json({ status: 409, title: "Topic Locked", detail: "Read the theory again before you continue practicing.", code: "TOPIC_LOCKED" }) });
      }
      attempts.push({ stepId, answer });
      const ok = ANSWERS[stepId](answer);
      if (ok) passed.add(stepId);
      if (stepId === 1 && lock) lock.mustReread = false;
      if (stepId !== 1) {
        const n = ok ? 0 : (streak.get(stepId) ?? 0) + 1;
        streak.set(stepId, n >= 3 ? 0 : n);
        if (n >= 3) { lock = { until: Date.now() + 5 * 60_000, mustReread: true }; passed.delete(1); }
      }
      return route.fulfill(json({ ...lockFields(), wrongStreak: streak.get(stepId) ?? 0, correct: ok, message: ok ? "Correct!" : "1 of 2 pairs are correct.",
        explanation: ok && stepId === 2 ? "Mọi field trạng thái đều private." : null, stepPassed: passed.has(stepId),
        attempts: attempts.filter((a) => a.stepId === stepId).length, progress: progressOf(), doneSteps: passed.size, stepCount: 5 }));
    }
    return route.fulfill(json({ ...TOPIC, ...lockFields(), progress: progressOf(), doneSteps: passed.size,
      steps: TOPIC.steps.map((s) => ({ ...s, passed: passed.has(s.id) })) }));
  });
  return { attempts, isSkipped: () => skipped, expireLock: () => { if (lock) lock.until = Date.now() - 1; } };
}

test("theory renders underline and colour from the visual editor but never runs scripts", async ({ page }) => {
  await installFullscreenMock(page);
  await setupMockApi(page);
  await setMockAuthSession(page, { username: "student", role: "USER" });
  const markdown = '## Rich\nMột <u>gạch</u> và <span style="color:#dc2626">đỏ</span>.'
    + ' <span style="position:fixed;inset:0">phủ</span> <img src=x onerror="window.__xss=1"><script>window.__xss=2</script>';
  await page.route(/\/api\/v1\/topics\/rich$/, (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({
    id: 9, slug: "rich", title: "Rich", estMinutes: 5, status: "PUBLISHED", progress: "NEW", stepCount: 1, doneSteps: 0,
    steps: [{ id: 1, position: 1, type: "THEORY", title: "Theory", passed: false, attempts: 0, content: { markdown } }],
  }) }));
  await page.goto("/topics/rich", { waitUntil: "domcontentloaded" });
  const theory = page.getByRole("region", { name: "Theory" });
  await expect(theory.locator("u")).toHaveText("gạch");
  await expect(theory.locator("span", { hasText: "đỏ" })).toHaveCSS("color", "rgb(220, 38, 38)");
  await expect(theory.locator("span", { hasText: "phủ" })).not.toHaveAttribute("style", /.+/);
  expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
});

test.describe("Topics: theory, quiz and drag-and-drop steps on project stages", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("the current stage shows a topic timeline with Start and Skip; skip is reversible", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/study-plans/oop", { waitUntil: "domcontentloaded" });
    const toggle = page.getByRole("button", { name: /Stage 1\/2: Stage 1/ });
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    const timeline = page.getByRole("list", { name: "Stage 1 topics" });
    await expect(timeline.getByRole("link", { name: "Start" })).toHaveAttribute("href", "/topics/oop-dong-goi?plan=oop&stage=1");
    await expect(timeline).toContainText("private + method có kiểm tra");
    await expect(timeline.getByRole("link", { name: /Stage 1/ })).toHaveAttribute("href", "/study-plans/oop/1/1");

    await timeline.getByRole("button", { name: "Skip" }).click();
    await expect.poll(api.isSkipped).toBe(true);
    await expect(timeline.getByText("Skipped")).toBeVisible();
    // Chủ đề kế tiếp được mở sẵn
    await expect(timeline.getByRole("link", { name: "Start" })).toHaveAttribute("href", /oop-ke-thua/);
    await timeline.getByRole("button", { name: "Undo" }).click();
    await expect.poll(api.isSkipped).toBe(false);

    // Stage không có chủ đề vẫn là một dòng như cũ
    await expect(page.getByText("Finish stage 1 first")).toBeVisible();
  });

  test("theory opens section by section and is marked as read at the end", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/topics/oop-dong-goi?plan=oop&stage=1", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("navigation", { name: "Breadcrumb" })).toContainText("OOP → Stage 1");
    const theory = page.getByRole("region", { name: "Theory" });
    await expect(theory.getByRole("heading", { name: "Vấn đề" })).toBeVisible();
    await expect(theory.getByRole("heading", { name: "Modifier" })).toHaveCount(0);

    await theory.getByRole("button", { name: "Read more" }).click();
    await expect(theory.getByRole("heading", { name: "Modifier" })).toBeVisible();
    expect(api.attempts).toHaveLength(0);
    await theory.getByRole("button", { name: "Expand all 3 sections" }).click();
    await expect(theory.getByRole("heading", { name: "Kiểm tra" })).toBeVisible();
    await expect.poll(() => api.attempts.map((a) => a.stepId)).toEqual([1]);
  });

  test("practice: wrong then right quiz, ordering with arrows, matching and fill-in", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/topics/oop-dong-goi", { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Go to practice" }).first().click();

    // Quiz
    const quiz = page.getByRole("region", { name: "Step 1 of 4" });
    await quiz.getByRole("button", { name: "See hint" }).click();
    await expect(quiz).toContainText("Trạng thái luôn giấu.");
    await quiz.getByLabel("balance").check();
    await quiz.getByRole("button", { name: "Submit" }).click();
    await expect(quiz.getByRole("status")).toContainText("Wrong answer");
    await quiz.getByLabel("owner").check();
    await quiz.getByRole("button", { name: "Try again" }).click();
    await expect(quiz.getByRole("status")).toContainText("Mọi field trạng thái đều private.");
    expect(api.attempts.find((a) => a.stepId === 2)?.answer).toEqual({ selected: ["a"] });
    await quiz.getByRole("button", { name: "Continue" }).click();

    // Sắp xếp bằng nút mũi tên (bàn phím được)
    const ordering = page.getByRole("region", { name: "Step 2 of 4" });
    await ordering.getByRole("button", { name: 'Move "if (amount > balance) throw ...;" up' }).click();
    await ordering.getByRole("button", { name: "Submit" }).click();
    await expect(ordering.getByRole("status")).toContainText("Correct!");
    expect(api.attempts.at(-1)?.answer).toEqual({ order: ["x", "y"] });
    await ordering.getByRole("button", { name: "Continue" }).click();

    // Ghép cặp: dòng i cột phải ghép với dòng i cột trái
    const matching = page.getByRole("region", { name: "Step 3 of 4" });
    await matching.getByRole("button", { name: 'Move "Chỉ trong lớp" up' }).click();
    await matching.getByRole("button", { name: "Submit" }).click();
    await expect(matching.getByRole("status")).toContainText("Correct!");
    expect(api.attempts.at(-1)?.answer).toEqual({ pairs: { l1: "r1", l2: "r2" } });
    await matching.getByRole("button", { name: "Continue" }).click();

    // Điền chỗ trống (theory chưa đọc ⇒ chưa DONE; đọc xong mới hoàn thành)
    const fill = page.getByRole("region", { name: "Step 4 of 4" });
    await fill.getByLabel("Blank 1").fill(" private ");
    await fill.getByRole("button", { name: "Submit" }).click();
    await expect(fill.getByRole("status")).toContainText("Correct!");
    await expect.poll(() => api.attempts.map((a) => a.stepId)).toContain(1);
    await page.getByRole("button", { name: "Finish topic" }).click();
    await expect(page.getByRole("region", { name: "Topic completed" })).toBeVisible();
  });

  test("three wrong answers in a row lock practice for 5 minutes until the theory is read again", async ({ page }) => {
    const api = await serve(page);
    await page.goto("/topics/oop-dong-goi", { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Go to practice" }).first().click();
    const quiz = page.getByRole("region", { name: "Step 1 of 4" });
    await quiz.getByLabel("Không field nào").check();
    await quiz.getByRole("button", { name: "Submit" }).click();
    await expect(quiz.getByRole("status")).toContainText("Wrong answer");
    await quiz.getByRole("button", { name: "Try again" }).click();
    await expect(quiz.getByRole("status")).toContainText("One more wrong answer locks practice for 5 minutes.");
    await quiz.getByRole("button", { name: "Try again" }).click();

    const locked = page.getByRole("region", { name: "Practice locked" });
    await expect(locked).toContainText(/[45]:\d\d/);
    await expect(locked).toContainText("Read the theory again while you wait.");
    // Lý thuyết bị đánh dấu chưa đọc; nút "Go to practice" không tự đánh dấu lại.
    await expect(page.getByRole("button", { name: "Theory: Lý thuyết", exact: true })).toBeVisible();

    await locked.getByRole("button", { name: "Read the theory" }).click();
    const theory = page.getByRole("region", { name: "Theory" });
    await expect(theory.getByRole("heading", { name: "Modifier" })).toHaveCount(0);
    await theory.getByRole("button", { name: "Expand all 3 sections" }).click();
    await expect(page.getByRole("button", { name: "Theory: Lý thuyết (done)" })).toBeVisible();
    await theory.getByRole("button", { name: "Go to practice" }).click();
    await expect(locked).toContainText("Theory done. Practice reopens when the timer ends.");

    api.expireLock();
    await page.reload();
    await expect(page.getByRole("region", { name: "Step 1 of 4" }).getByRole("button", { name: "Submit" })).toBeVisible();
  });
});
