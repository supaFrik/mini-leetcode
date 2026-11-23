import { test, expect, type Page, type Route } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Course Builder (specs/course-builder.spec.md) trên một server giả trong bộ nhớ: plan, đề, topic, clone, unlock,
 * publish-check, publish. Ghi lại mọi lời gọi ghi để kiểm tra hợp đồng API.
 */
interface Problem { id: number; slug: string; title: string; description: string; status: string; learners: number; type?: string }
interface Entry { type: "LESSON" | "PROBLEM" | "STAGE"; topicId?: number; problemId?: number }
interface PlanState {
  slug: string; title: string; description: string | null; status: string; createdById: number; updatedAt: string;
  learnerCount: number; sessions: { title: string; description?: string; entries: Entry[] }[];
}

const slugOf = (t: string) => t.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

function fake(page: Page, opts: { plan?: Partial<PlanState>; problems?: Problem[]; topics?: any[] } = {}) {
  const problems = new Map<number, Problem>((opts.problems ?? []).map((p) => [p.id, { type: "OOP_GRINDING", ...p }]));
  const topics = new Map<number, any>((opts.topics ?? []).map((t) => [t.id, { ...t }]));
  const plans = new Map<string, PlanState>();
  if (opts.plan) {
    const p = { slug: "oop", title: "OOP", description: null, status: "DRAFT", createdById: 7, updatedAt: "2026-11-13T10:00:00",
      learnerCount: 0, sessions: [], ...opts.plan } as PlanState;
    plans.set(p.slug, p);
  }
  const writes: { method: string; path: string; body: any }[] = [];
  const state = { checkErrors: [] as any[], nextId: 500, tick: 0 };
  const json = (route: Route, data: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
  const topicRef = (id: number) => ({ ...topics.get(id), progress: "NEW", estMinutes: 10, stepCount: 1, doneSteps: 0 });
  const problemIdsOf = (p: PlanState) => p.sessions.flatMap((x) => x.entries.flatMap((e) => (e.problemId ? [e.problemId] : [])));
  const detail = (p: PlanState) => ({
    id: 1, slug: p.slug, title: p.title, description: p.description, status: p.status, createdById: p.createdById,
    createdByUsername: "teacher", updatedAt: p.updatedAt, learnerCount: p.learnerCount,
    stageCount: problemIdsOf(p).length, doneStages: 0,
    items: p.sessions.map((it, position) => {
      const ids = it.entries.flatMap((e) => (e.problemId ? [e.problemId] : []));
      return {
        id: position + 1, position, kind: "SESSION", title: it.title, description: it.description ?? null, done: false,
        stages: ids.map((id, k) => {
          const pr = problems.get(id)!;
          return { stageNo: k + 1, problemId: id, slug: pr.slug, title: pr.title, difficulty: "EASY", problemType: pr.type,
            published: pr.status === "PUBLISHED", status: "NEVER", problemStatus: pr.status, learnersOnStage: pr.learners, topics: [] };
        }),
        entries: it.entries.map((e) => (e.topicId ? { type: e.type, topic: topicRef(e.topicId) }
          : { type: e.type, stageNo: ids.indexOf(e.problemId!) + 1 })),
      };
    }),
  });
  const problemJson = (p: Problem) => ({ ...p, difficulty: "EASY", timeLimitMs: 1000, memoryLimitMb: 256, tags: [], type: p.type ?? "OOP_GRINDING",
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
      const p: PlanState = { slug: slugOf(body.title), title: body.title, description: null, status: "DRAFT", createdById: 7,
        updatedAt: `2026-11-13T10:00:0${state.tick++}`, learnerCount: 0, sessions: body.sessions };
      plans.set(p.slug, p);
      return json(route, detail(p), 201);
    }
    let m = /^\/api\/v1\/study-plans\/([^/]+)(\/publish-check|\/publish|\/transitions)?$/.exec(path);
    if (m) {
      const plan = plans.get(decodeURIComponent(m[1]));
      if (!plan) return json(route, { status: 404, detail: "Study plan not found" }, 404);
      if (m[2] === "/publish-check") {
        const toPublish = problemIdsOf(plan).filter((id) => problems.get(id)!.status !== "PUBLISHED");
        return json(route, { ok: state.checkErrors.length === 0, errors: state.checkErrors, warnings: [], problemsToPublish: toPublish, topicsToPublish: [] });
      }
      if (m[2] === "/publish") {
        problemIdsOf(plan).forEach((id) => { problems.get(id)!.status = "PUBLISHED"; });
        plan.status = "PUBLISHED";
        return json(route, detail(plan));
      }
      if (m[2] === "/transitions") { plan.status = body.status; return json(route, detail(plan)); }
      if (method === "PUT") {
        if (body.expectedUpdatedAt && body.expectedUpdatedAt !== plan.updatedAt) {
          return json(route, { status: 409, detail: "This study plan was changed in another tab or by another user. Reload to continue." }, 409);
        }
        plans.delete(plan.slug);
        Object.assign(plan, { slug: plan.status === "DRAFT" ? slugOf(body.title) : plan.slug, title: body.title,
          description: body.description ?? null, sessions: body.sessions,
          updatedAt: `2026-11-13T10:01:${String(state.tick++).padStart(2, "0")}` });
        plans.set(plan.slug, plan);
        return json(route, detail(plan));
      }
      return json(route, detail(plan));
    }
    // ── problems ──
    if (path === "/api/v1/problems" && method === "POST") {
      const p = { id: state.nextId++, slug: `p-${state.nextId}`, title: body.title, description: body.description, status: "DRAFT", learners: 0, type: body.type };
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
      if (m[2] === "authoring") return json(route, { problemId: src.id, status: src.status, problemType: src.type, gradingConfig: null, referenceSolution: [] });
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
const item = (page: Page, label: string) =>
  outline(page).getByRole("button", { name: new RegExp(`^(Lesson|Problem|Project stage) ${label}`) });

test.describe("Course builder", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { id: 7, username: "teacher", role: "INSTRUCTOR" });
  });

  test("AC-CB-01/02: new plan opens with blank sessions; a new problem is added, autosaved and kept after reload", async ({ page }) => {
    const api = fake(page);
    await api.ready;
    await page.goto("/admin/study-plans/new");
    const dialog = page.getByRole("dialog", { name: "New study plan" });
    await dialog.getByLabel("Title").fill("OOP with Java");
    await dialog.getByLabel("Number of sessions").fill("2");
    await dialog.getByRole("button", { name: "Create" }).click();
    await expect(page).toHaveURL(/\/admin\/study-plans\/oop-with-java/);
    expect(api.writes.find((w) => w.path === "/api/v1/study-plans")?.body.sessions).toHaveLength(2);
    await expect(outline(page).getByRole("button", { name: "Session 2", exact: true })).toBeVisible();

    await outline(page).getByRole("button", { name: "Add to Session 1" }).click();
    await page.getByRole("menuitem", { name: "Add problem" }).click();
    await editor(page).getByLabel("Search problems").fill("Mini Bank");
    await editor(page).getByRole("button", { name: "Create new problem" }).click();
    await expect(item(page, "Mini Bank")).toBeVisible();
    expect(api.writes.filter((w) => w.path === "/api/v1/problems" && w.method === "POST")).toHaveLength(1);

    await expect.poll(() => api.lastPut()?.sessions?.[0]?.entries).toEqual([{ type: "PROBLEM", problemId: 500 }]);
    await expect(page.getByRole("status").filter({ hasText: "All changes saved" })).toBeVisible({ timeout: 10_000 });

    await page.reload();
    await expect(item(page, "Mini Bank")).toBeVisible();
  });

  test("AC-CB-03: leaving with unsaved changes asks first", async ({ page }) => {
    const api = fake(page, { plan: { slug: "oop", title: "OOP", sessions: [{ title: "Intro", entries: [] }] } });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await editor(page).getByLabel("Session title").fill("Changed");
    await page.getByRole("link", { name: "← Study plans" }).click();
    const asked = page.getByRole("alertdialog");
    await expect(asked).toContainText("Leave without saving?");
    await asked.getByRole("button", { name: "Cancel" }).click();
    await expect(asked).toHaveCount(0);
    await expect(page).toHaveURL(/\/admin\/study-plans\/oop/);
  });

  test("AC-CB-04/05: the checklist blocks publishing and points to the problem; a clean check publishes everything", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", sessions: [{ title: "Bank", entries: [{ type: "STAGE", problemId: 1 }, { type: "STAGE", problemId: 2 }] }] },
      problems: [{ id: 1, slug: "b1", title: "Bank 1", description: "d", status: "DRAFT", learners: 0 },
        { id: 2, slug: "b2", title: "Bank 2", description: "d", status: "REVIEWING", learners: 0 }],
    });
    await api.ready;
    api.state.checkErrors = [{ node: "stage", itemIndex: 0, problemId: 2, message: "Bank · stage 2: no public test case" }];
    await page.goto("/admin/study-plans/oop");
    await page.getByRole("button", { name: "Plan settings" }).click();
    await page.getByRole("button", { name: "Publish…" }).click();
    const dialog = page.getByRole("dialog", { name: "Publish OOP" });
    await expect(dialog.getByRole("list", { name: "Publish checklist" })).toContainText("stage 2: no public test case");
    await expect(dialog).toContainText("Will publish: plan, 2 draft problems");
    await expect(dialog.getByRole("button", { name: "Publish" })).toBeDisabled();
    await dialog.getByRole("button", { name: "Go to" }).click();
    await expect(page).toHaveURL(/node=entry%3A/);
    await expect(item(page, "Bank 2")).toHaveAttribute("aria-current", "true");

    api.state.checkErrors = [];
    await page.getByRole("button", { name: "Plan settings" }).click();
    await page.getByRole("button", { name: "Publish…" }).click();
    await page.getByRole("dialog", { name: "Publish OOP" }).getByRole("button", { name: "Publish" }).click();
    await expect(page.getByText("PUBLISHED", { exact: true })).toBeVisible();
    await expect(page.getByRole("status").filter({ hasText: "Live · 0 learners" })).toBeVisible();
    expect(api.writes.some((w) => w.path === "/api/v1/study-plans/oop/publish")).toBe(true);
    expect(Array.from(api.problems.values()).map((p) => p.status)).toEqual(["PUBLISHED", "PUBLISHED"]);
  });

  test("AC-CB-07: unlocking a live problem names its learners and leaves it in draft until republished", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", status: "PUBLISHED", learnerCount: 23,
        sessions: [{ title: "Bank", entries: [{ type: "STAGE", problemId: 4 }] }] },
      problems: [{ id: 4, slug: "b4", title: "Bank 4", description: "d", status: "PUBLISHED", learners: 5 }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await expect(page.getByRole("status").filter({ hasText: "Live · 23 learners" })).toBeVisible();
    await item(page, "Bank 4").click();
    await editor(page).getByRole("tab", { name: "Tests" }).click();
    await expect(editor(page)).toContainText("Locked while live");
    await editor(page).getByRole("button", { name: "Unlock to edit" }).click();
    const asked = page.getByRole("alertdialog");
    await expect(asked).toContainText("5 learners currently on it");
    await asked.getByRole("button", { name: "Unlock" }).click();
    await expect(page.getByRole("status").filter({ hasText: "1 problem unlocked" })).toBeVisible();
    expect(api.problems.get(4)!.status).toBe("DRAFT");
  });

  test("AC-CB-08: Alt+Arrow moves an item within its session", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", sessions: [{ title: "Bank", entries: [
        { type: "STAGE", problemId: 1 }, { type: "STAGE", problemId: 2 }, { type: "LESSON", topicId: 9 }, { type: "STAGE", problemId: 3 }] }] },
      problems: [1, 2, 3].map((id) => ({ id, slug: `b${id}`, title: `Bank ${id}`, description: "d", status: "DRAFT", learners: 0 })),
      topics: [{ id: 9, slug: "t9", title: "Interfaces", status: "DRAFT", stepCount: 1 }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await item(page, "Bank 3").click();
    await item(page, "Bank 3").focus();
    await page.keyboard.press("Alt+ArrowUp");
    await page.keyboard.press("Alt+ArrowUp");
    await expect.poll(() => api.lastPut()?.sessions?.[0]?.entries).toEqual([
      { type: "STAGE", problemId: 1 }, { type: "STAGE", problemId: 3 }, { type: "STAGE", problemId: 2 }, { type: "LESSON", topicId: 9 }]);
  });

  test("a session adds an existing lesson from the library and saves it with the plan", async ({ page }) => {
    const api = fake(page, {
      plan: { slug: "oop", title: "OOP", sessions: [{ title: "Bank", entries: [{ type: "STAGE", problemId: 1 }] }] },
      problems: [{ id: 1, slug: "b1", title: "Bank 1", description: "d", status: "DRAFT", learners: 0 }],
      topics: [{ id: 55, slug: "encapsulation", title: "Encapsulation", status: "PUBLISHED" }],
    });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await outline(page).getByRole("button", { name: "Add to Bank" }).click();
    await page.getByRole("menuitem", { name: "Add lesson" }).click();
    await editor(page).getByLabel("Search lessons").fill("encap");
    await editor(page).getByRole("list", { name: "Lesson results" }).getByRole("button", { name: /Encapsulation/ }).click();
    await expect(item(page, "Encapsulation")).toBeVisible();
    await expect.poll(() => api.lastPut()?.sessions?.[0]?.entries).toEqual([{ type: "STAGE", problemId: 1 }, { type: "LESSON", topicId: 55 }]);
  });

  test("another instructor's plan is read-only", async ({ page }) => {
    const api = fake(page, { plan: { slug: "oop", title: "OOP", createdById: 99, sessions: [{ title: "Intro", entries: [] }] } });
    await api.ready;
    await page.goto("/admin/study-plans/oop");
    await expect(page.getByRole("note")).toContainText("Read-only");
    await expect(editor(page).getByLabel("Session title")).toBeDisabled();
    await expect(outline(page).getByRole("button", { name: "Add session" })).toHaveCount(0);
    await page.getByRole("button", { name: "Plan settings" }).click();
    await expect(page.getByRole("button", { name: "Publish…" })).toHaveCount(0);
  });
});
