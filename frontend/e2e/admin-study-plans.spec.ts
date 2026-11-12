import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/** Đề PUBLISHED cho ô tìm kiếm (GET /api/v1/problems?status=PUBLISHED&search=...). */
const PROBLEMS = [
  { id: 101, slug: "mini-bank-stage-1", title: "Mini Bank 1/5", difficulty: "EASY", status: "PUBLISHED", tags: [] },
  { id: 102, slug: "mini-bank-stage-2", title: "Mini Bank 2/5", difficulty: "EASY", status: "PUBLISHED", tags: [] },
  { id: 201, slug: "two-sum", title: "Two Sum", difficulty: "EASY", status: "PUBLISHED", tags: [] },
];

type Plan = Record<string, any>;

/** Server plan giả trong bộ nhớ; ghi lại body POST/PUT/transitions để kiểm tra hợp đồng. */
async function serveAuthoring(page: Page, initial: Plan[] = []) {
  const plans = new Map<string, Plan>(initial.map((p) => [p.slug, p]));
  const writes: { method: string; path: string; body: any }[] = [];
  const toDetail = (slug: string, body: any, old?: Plan): Plan => ({
    id: old?.id ?? plans.size + 1, slug: body.slug, title: body.title, description: body.description ?? null,
    status: old?.status ?? "DRAFT", createdById: old?.createdById ?? 7, createdByUsername: old?.createdByUsername ?? "teacher",
    stageCount: 0, doneStages: 0,
    items: body.items.map((it: any, position: number) => ({
      id: position + 1, position, kind: it.kind, title: it.title, description: it.description ?? null, done: false,
      stages: it.problemIds.map((id: number, k: number) => {
        const p = PROBLEMS.find((x) => x.id === id)!;
        return { stageNo: k + 1, problemId: id, slug: p.slug, title: p.title, difficulty: p.difficulty, problemType: "OOP_GRINDING", published: true, status: "NEVER" };
      }),
    })),
  });

  await page.route(/\/api\/v1\/problems\?/, (route) => {
    const q = (new URL(route.request().url()).searchParams.get("search") ?? "").toLowerCase();
    const content = PROBLEMS.filter((p) => p.title.toLowerCase().includes(q) || p.slug.includes(q));
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } }) });
  });

  await page.route(/\/api\/v1\/study-plans(\/.*)?$/, async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    const method = req.method();
    const body = req.postData() ? JSON.parse(req.postData()!) : null;
    const ok = (data: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
    if (method !== "GET") writes.push({ method, path, body });

    if (path === "/api/v1/study-plans" && method === "GET") {
      return ok(Array.from(plans.values()).map(({ items, ...s }) => ({ ...s, itemCount: items.length })));
    }
    if (path === "/api/v1/study-plans" && method === "POST") {
      const plan = toDetail(body.slug, body);
      plans.set(plan.slug, plan);
      return ok(plan, 201);
    }
    const m = path.match(/^\/api\/v1\/study-plans\/([^/]+)(\/transitions)?$/);
    const plan = m ? plans.get(decodeURIComponent(m[1])) : undefined;
    if (!plan) return ok({ status: 404, detail: "Study plan not found" }, 404);
    if (m![2] && method === "POST") {
      plan.status = body.status;
      return ok(plan);
    }
    if (method === "PUT") {
      if (body.items.some((it: any) => it.problemIds.includes(201))) {
        return route.fulfill({ status: 409, contentType: "application/problem+json",
          body: JSON.stringify({ status: 409, detail: "Problem already belongs to another study plan item: two-sum" }) });
      }
      const updated = toDetail(body.slug, body, plan);
      plans.delete(plan.slug);
      plans.set(updated.slug, updated);
      return ok(updated);
    }
    return ok(plan);
  });
  return { plans, writes };
}

const existing = (createdById: number): Plan => ({
  id: 1, slug: "oop-voi-java", title: "OOP với Java", description: null, status: "PUBLISHED",
  createdById, createdByUsername: createdById === 7 ? "teacher" : "other_teacher", stageCount: 1, doneStages: 0,
  items: [{ id: 1, position: 0, kind: "PROBLEM", title: "Warm-up", description: null, done: false,
    stages: [{ stageNo: 1, problemId: 201, slug: "two-sum", title: "Two Sum", difficulty: "EASY", problemType: "ALGORITHMIC", published: true, status: "NEVER" }] }],
});

test.describe("/admin/study-plans authoring", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { id: 7, username: "teacher", role: "INSTRUCTOR" });
  });

  test("creates a project plan from searched problems, reorders stages and publishes", async ({ page }) => {
    const server = await serveAuthoring(page);
    await page.goto("/admin/study-plans/new");

    await page.getByLabel("Title", { exact: true }).fill("Java cơ bản");
    await expect(page.getByLabel("Slug (URL)")).toHaveValue("java-co-ban");
    await page.getByLabel("Item title").fill("Mini Bank");

    const search = page.getByLabel("Search published problems");
    await search.fill("mini bank");
    await page.getByRole("button", { name: /Mini Bank 1\/5/ }).click();
    await search.fill("stage-2");
    await page.getByRole("button", { name: /Mini Bank 2\/5/ }).click();
    await page.getByRole("button", { name: "Move Mini Bank 2/5 up" }).click();

    const stages = page.getByRole("list", { name: "Item 1 stages" }).getByRole("listitem");
    await expect(stages.nth(0)).toContainText("Stage 1");
    await expect(stages.nth(0)).toContainText("Mini Bank 2/5");

    await page.getByRole("button", { name: "Create draft" }).click();
    await expect(page).toHaveURL(/\/admin\/study-plans\/java-co-ban$/);
    await expect(page.getByText("DRAFT", { exact: true })).toBeVisible();
    expect(server.writes[0]).toMatchObject({
      method: "POST", path: "/api/v1/study-plans",
      body: { slug: "java-co-ban", title: "Java cơ bản", items: [{ kind: "PROJECT", title: "Mini Bank", problemIds: [102, 101] }] },
    });

    await page.getByRole("button", { name: "Publish" }).click();
    await expect(page.getByText("PUBLISHED", { exact: true })).toBeVisible();
    expect(server.writes.at(-1)).toMatchObject({ path: "/api/v1/study-plans/java-co-ban/transitions", body: { status: "PUBLISHED" } });
  });

  test("validates locally before calling the server", async ({ page }) => {
    const server = await serveAuthoring(page);
    await page.goto("/admin/study-plans/new");
    await page.getByLabel("Title", { exact: true }).fill("Empty plan");
    await page.getByLabel("Item title").fill("Project");
    await page.getByRole("button", { name: "Create draft" }).click();

    await expect(page.getByRole("alert").filter({ hasText: "Item 1" })).toContainText("needs at least one problem");
    expect(server.writes).toHaveLength(0);
  });

  test("another instructor's plan is read-only; the list marks it", async ({ page }) => {
    await serveAuthoring(page, [existing(99)]);
    await page.goto("/admin/study-plans");
    const row = page.getByRole("region", { name: "Study plans" }).getByRole("row", { name: /OOP với Java/ });
    await expect(row).toContainText("other_teacher");
    await expect(row).toContainText("Read-only");

    await page.getByRole("link", { name: "OOP với Java" }).click();
    await expect(page.getByRole("note")).toContainText("only other_teacher or an admin");
    await expect(page.getByLabel("Title", { exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Save changes" })).toHaveCount(0);
  });

  test("shows the server's reason when a save is rejected", async ({ page }) => {
    await serveAuthoring(page, [existing(7)]);
    await page.goto("/admin/study-plans/oop-voi-java");
    await page.getByLabel("Title", { exact: true }).fill("OOP với Java (2026)");
    await page.getByRole("button", { name: "Save changes" }).click();

    await expect(page.getByRole("alert").filter({ hasText: "already belongs" })).toContainText("two-sum");
  });
});
