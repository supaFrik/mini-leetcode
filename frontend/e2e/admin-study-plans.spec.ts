import { test, expect, type Page, type Route } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Course Builder (specs/course-builder.spec.md) trên một server giả trong bộ nhớ: plan, đề, topic, clone, unlock,
 * publish-check, publish. Ghi lại mọi lời gọi ghi để kiểm tra hợp đồng API.
 */
interface Problem { id: number; slug: string; title: string; description: string; status: string; learners: number }
interface PlanState {
  slug: string; title: string; description: string | null; status: string; createdById: number; updatedAt: string;
  learnerCount: number; items: { kind: string; title: string; description?: string; problemIds: number[]; stageTopics: number[][] }[];
}

function fake(page: Page, opts: { plan?: Partial<PlanState>; problems?: Problem[]; topics?: any[] } = {}) {
  const problems = new Map<number, Problem>((opts.problems ?? []).map((p) => [p.id, { ...p }]));
  const topics = new Map<number, any>((opts.topics ?? []).map((t) => [t.id, { ...t }]));
  const plans = new Map<string, PlanState>();
  if (opts.plan) {
    const p = { slug: "oop", title: "OOP", description: null, status: "DRAFT", createdById: 7, updatedAt: "2026-11-13T10:00:00",
      learnerCount: 0, items: [], ...opts.plan } as PlanState;
    plans.set(p.slug, p);
  }
  const writes: { method: string; path: string; body: any }[] = [];
  const state = { checkErrors: [] as any[], nextId: 500, tick: 0 };
  const json = (route: Route, data: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
  const detail = (p: PlanState) => ({
    id: 1, slug: p.slug, title: p.title, description: p.description, status: p.status, createdById: p.createdById,
    createdByUsername: "teacher", updatedAt: p.updatedAt, learnerCount: p.learnerCount,
    stageCount: p.items.reduce((n, i) => n + i.problemIds.length, 0), doneStages: 0,
    items: p.items.map((it, position) => ({
      id: position + 1, position, kind: it.kind, title: it.title, description: it.description ?? null, done: false,
      stages: it.problemIds.map((id, k) => {
        const pr = problems.get(id)!;
        return { stageNo: k + 1, problemId: id, slug: pr.slug, title: pr.title, difficulty: "EASY", problemType: "OOP_GRINDING",
          published: pr.status === "PUBLISHED", status: "NEVER", problemStatus: pr.status, learnersOnStage: pr.learners,
          topics: (it.stageTopics[k] ?? []).map((tid) => ({ ...topics.get(tid), progress: "NEW", estMinutes: 10, stepCount: 1, doneSteps: 0 })) };
      }),
    })),
  });
  const problemJson = (p: Problem) => ({ ...p, difficulty: "EASY", timeLimitMs: 1000, memoryLimitMb: 256, tags: [], type: "OOP_GRINDING",
    starterTemplates: null, testCaseCount: 0 });

  const ready = page.route(/\/api\/v1\/(study-plans|problems|admin\/problems|topics)(\/.*)?(\?.*)?$/, async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const method = req.method();
    const body = req.postData() && !req.headers()["content-type"]?.includes("multipart") ? JSON.parse(req.postData()!) : null;
    if (method !== "GET") writes.push({ method, path, body });

    // ── study plans ──
    if (path === "/api/v1/study-plans" && method === "POST") {
      const p: PlanState = { slug: body.slug, title: body.title, description: null, status: "DRAFT", createdById: 7,
        updatedAt: `2026-11-13T10:00:0${state.tick++}`, learnerCount: 0, items: [] };
      plans.set(p.slug, p);
      return json(route, detail(p), 201);
    }
    let m = /^\/api\/v1\/study-plans\/([^/]+)(\/publish-check|\/publish|\/transitions)?$/.exec(path);
    if (m) {
      const plan = plans.get(decodeURIComponent(m[1]));
      if (!plan) return json(route, { status: 404, detail: "Study plan not found" }, 404);
      if (m[2] === "/publish-check") {
        const toPublish = plan.items.flatMap((i) => i.problemIds).filter((id) => problems.get(id)!.status !== "PUBLISHED");
        return json(route, { ok: state.checkErrors.length === 0, errors: state.checkErrors, warnings: [], problemsToPublish: toPublish, topicsToPublish: [] });
      }
      if (m[2] === "/publish") {
        plan.items.flatMap((i) => i.problemIds).forEach((id) => { problems.get(id)!.status = "PUBLISHED"; });
        plan.status = "PUBLISHED";
        return json(route, detail(plan));
      }
      if (m[2] === "/transitions") { plan.status = body.status; return json(route, detail(plan)); }
      if (method === "PUT") {
        if (body.expectedUpdatedAt && body.expectedUpdatedAt !== plan.updatedAt) {
          return json(route, { status: 409, detail: "This study plan was changed in another tab or by another user. Reload to continue." }, 409);
        }
        plans.delete(plan.slug);
        Object.assign(plan, { slug: body.slug, title: body.title, description: body.description ?? null, items: body.items,
          updatedAt: `2026-11-13T10:01:${String(state.tick++).padStart(2, "0")}` });
        plans.set(plan.slug, plan);
        return json(route, detail(plan));
      }
      return json(route, detail(plan));
    }
    // ── problems ──
    if (path === "/api/v1/problems" && method === "POST") {
      const p = { id: state.nextId++, slug: `p-${state.nextId}`, title: body.title, description: body.description, status: "DRAFT", learners: 0 };
      problems.set(p.id, p);
      return json(route, problemJson(p), 201);
    }
    if (path === "/api/v1/problems" && method === "GET") {
      const q = (url.searchParams.get("search") ?? "").toLowerCase();
      const content = Array.from(problems.values()).filter((p) => p.title.toLowerCase().includes(q)).map(problemJson);
      return json(route, { content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } });
    }
    m = /^\/api\/v1\/admin\/problems\/(\d+)\/(clone|unlock|authoring)$/.exec(path);
    if (m) {
      const src = problems.get(Number(m[1]))!;
      if (m[2] === "authoring") return json(route, { problemId: src.id, status: src.status, problemType: "OOP_GRINDING", gradingConfig: null, referenceSolution: [] });
      if (m[2] === "unlock") { src.status = "DRAFT"; return json(route, problemJson(src)); }
      const copy = { ...src, id: state.nextId++, slug: `p-${state.nextId}`, title: body.title, status: "DRAFT", learners: 0 };
      problems.set(copy.id, copy);
      return json(route, problemJson(copy), 201);
    }
    m = /^\/api\/v1\/problems\/(\d+)(\/test-cases)?$/.exec(path);
    if (m) {
      const p = problems.get(Number(m[1]));
      if (!p) return json(route, { status: 404, detail: "Problem not found" }, 404);
      if (m[2]) return json(route, []);
      if (method === "PUT") Object.assign(p, { title: body.title, description: body.description });
      return json(route, problemJson(p));
    }
    // ── topics ──
    if (path === "/api/v1/topics" && method === "GET") {
      return json(route, Array.from(topics.values()).map((t) => ({ ...t, estMinutes: 10, stepCount: 1, createdById: 7, createdByUsername: "teacher" })));
    }
    return json(route, { status: 404, detail: `Unhandled ${method} ${path}` }, 404);
  });
  return { ready, writes, problems, plans, state, lastPut: () => [...writes].reverse().find((w) => w.method === "PUT" && w.path.startsWith("/api/v1/study-plans/"))?.body };
}

const outline = (page: Page) => page.getByRole("navigation", { name: "Course outline" });
const editor = (page: Page) => page.getByRole("region", { name: /^Editor/ });

test.describe("Course builder", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { id: 7, username: "teacher", role: "INSTRUCTOR" });
  });

  test("AC-CB-01/02: new plan, blank first stage, + stage clones the last one, autosaved and kept after reload", async ({ page }) => {
    const api = fake(page);
    await api.ready;
    await page.goto("/admin/study-plans/new");
    await expect(page).toHaveURL(/\/admin\/study-plans\/untitled-[a-z0-9]+\?node=plan$/);

    await editor(page).getByLabel("Title").fill("OOP with Java");
    await outline(page).getByRole("button", { name: "project", exact: true }).click();
    await editor(page).getByLabel("Title").fill("Mini Bank");
    await editor(page).getByRole("button", { name: "Blank problem" }).click();
    await expect(outline(page).getByRole("button", { name: "1 Mini Bank 1", exact: true })).toBeVisible();
    await outline(page).getByRole("button", { name: "stage", exact: true }).click();
    await expect(outline(page).getByRole("button", { name: "2 Mini Bank 2", exact: true })).toBeVisible();
    await outline(page).getByRole("button", { name: "stage", exact: true }).click();
    await expect(outline(page).getByRole("button", { name: "3 Mini Bank 3", exact: true })).toBeVisible();

    // 1 đề trống + 2 lần clone từ stage trước, không rời trang
    const created = api.writes.filter((w) => w.path === "/api/v1/problems" && w.method === "POST");
    const clones = api.writes.filter((w) => w.path.endsWith("/clone"));
    expect(created).toHaveLength(1);
    expect(clones.map((w) => w.path)).toEqual([`/api/v1/admin/problems/${500}/clone`, `/api/v1/admin/problems/${501}/clone`]);
    expect(Array.from(api.problems.values()).every((p) => p.status === "DRAFT")).toBe(true);

    await expect(page.getByRole("status").filter({ hasText: /Saved|All changes saved/ })).toBeVisible({ timeout: 10_000 });
    await expect.poll(() => api.lastPut()?.items?.[0]?.problemIds).toEqual([500, 501, 502]);
    expect(api.lastPut()).toMatchObject({ slug: "oop-with-java", title: "OOP with Java" });
    await expect(page).toHaveURL(/\/admin\/study-plans\/oop-with-java\?node=stage%3A502$/);

    await page.reload();
    await expect(outline(page).getByRole("button", { name: "3 Mini Bank 3", exact: true })).toBeVisible();
    await expect(page.getByRole("region", { name: "Editor: Stage 3 · Mini Bank 3" })).toBeVisible();
  });

  test("AC-CB-03: leaving with unsaved changes asks first", async ({ page }) => {
    const api = fake(page, { plan: { slug: "oop", title: "OOP" } });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await editor(page).getByLabel("Description").fill("Changed");
    await page.getByRole("link", { name: "← Study plans" }).click();
    const asked = page.getByRole("alertdialog");
    await expect(asked).toContainText("Leave without saving?");
    await asked.getByRole("button", { name: "Cancel" }).click();
    await expect(asked).toHaveCount(0);
    await expect(page).toHaveURL(/\/admin\/study-plans\/oop/);
  });

  test("AC-CB-04/05: the checklist blocks publishing and points to the stage; a clean check publishes everything", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", items: [{ kind: "PROJECT", title: "Bank", problemIds: [1, 2], stageTopics: [[], []] }] },
      problems: [{ id: 1, slug: "b1", title: "Bank 1", description: "d", status: "DRAFT", learners: 0 },
        { id: 2, slug: "b2", title: "Bank 2", description: "d", status: "REVIEWING", learners: 0 }],
    });
    await api.ready;
    api.state.checkErrors = [{ node: "stage", itemIndex: 0, problemId: 2, message: "Bank · stage 2: no public test case" }];
    await page.goto("/admin/study-plans/oop");
    await page.getByRole("button", { name: "Publish…" }).click();
    const dialog = page.getByRole("dialog", { name: 'Publish OOP' });
    await expect(dialog.getByRole("list", { name: "Publish checklist" })).toContainText("stage 2: no public test case");
    await expect(dialog).toContainText("Will publish: plan, 2 draft problems");
    await expect(dialog.getByRole("button", { name: "Publish" })).toBeDisabled();
    await dialog.getByRole("button", { name: "Go to" }).click();
    await expect(page).toHaveURL(/node=stage%3A2$/);
    await expect(outline(page).getByLabel("Has publish errors")).toHaveCount(1);

    api.state.checkErrors = [];
    await page.getByRole("button", { name: "Publish…" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Publish" }).click();
    await expect(page.getByText("PUBLISHED", { exact: true })).toBeVisible();
    await expect(page.getByRole("status").filter({ hasText: "Live · 0 learners" })).toBeVisible();
    expect(api.writes.some((w) => w.path === "/api/v1/study-plans/oop/publish")).toBe(true);
    expect(Array.from(api.problems.values()).map((p) => p.status)).toEqual(["PUBLISHED", "PUBLISHED"]);
  });

  test("AC-CB-07: unlocking a live stage names its learners and leaves it in draft until republished", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", status: "PUBLISHED", learnerCount: 23,
        items: [{ kind: "PROJECT", title: "Bank", problemIds: [4], stageTopics: [[]] }] },
      problems: [{ id: 4, slug: "b4", title: "Bank 4", description: "d", status: "PUBLISHED", learners: 5 }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop?node=stage%3A4");
    await expect(page.getByRole("status").filter({ hasText: "Live · 23 learners" })).toBeVisible();
    await editor(page).getByRole("tab", { name: "Tests" }).click();
    await expect(editor(page)).toContainText("Locked while live");
    await editor(page).getByRole("button", { name: "Unlock to edit" }).click();
    const asked = page.getByRole("alertdialog");
    await expect(asked).toContainText("5 learners currently on it");
    await asked.getByRole("button", { name: "Unlock" }).click();
    await expect(page.getByRole("status").filter({ hasText: "1 stage unlocked" })).toBeVisible();
    expect(api.problems.get(4)!.status).toBe("DRAFT");
  });

  test("AC-CB-08: Alt+Arrow moves a stage and its topics travel with it", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", items: [{ kind: "PROJECT", title: "Bank", problemIds: [1, 2, 3], stageTopics: [[], [], [9]] }] },
      problems: [1, 2, 3].map((id) => ({ id, slug: `b${id}`, title: `Bank ${id}`, description: "d", status: "DRAFT", learners: 0 })),
      topics: [{ id: 9, slug: "t9", title: "Interfaces", status: "DRAFT" }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop?node=stage%3A3");
    await outline(page).getByRole("button", { name: "3 Bank 3", exact: true }).focus();
    await page.keyboard.press("Alt+ArrowUp");
    await page.keyboard.press("Alt+ArrowUp");
    await expect(outline(page).getByRole("button", { name: "1 Bank 3", exact: true })).toBeVisible();
    await expect.poll(() => api.lastPut()?.items?.[0]).toMatchObject({ problemIds: [3, 1, 2], stageTopics: [[9], [], []] });
  });

  test("a stage attaches an existing topic from search and saves it with the plan", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", items: [{ kind: "PROJECT", title: "Bank", problemIds: [1], stageTopics: [[]] }] },
      problems: [{ id: 1, slug: "b1", title: "Bank 1", description: "d", status: "DRAFT", learners: 0 }],
      topics: [{ id: 55, slug: "encapsulation", title: "Encapsulation", status: "PUBLISHED" }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop?node=stage%3A1");
    await editor(page).getByLabel("Find or create a topic").fill("encap");
    await editor(page).getByRole("list", { name: "Topic results" }).getByRole("button", { name: /Encapsulation/ }).click();
    await expect(outline(page).getByRole("button", { name: "Encapsulation", exact: true })).toBeVisible();
    await expect.poll(() => api.lastPut()?.items?.[0]?.stageTopics).toEqual([[55]]);
  });

  test("another instructor's plan is read-only", async ({ page }) => {
    const api = fake(page, { plan: { slug: "oop", title: "OOP", createdById: 99 } });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await expect(page.getByRole("note")).toContainText("Read-only");
    await expect(editor(page).getByLabel("Title")).toBeDisabled();
    await expect(page.getByRole("button", { name: "Publish…" })).toHaveCount(0);
  });
});
