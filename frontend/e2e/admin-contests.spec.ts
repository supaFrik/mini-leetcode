import { test, expect, type Page, type Route } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { AdminContestDetail, AdminParticipant, ContestStatus } from "../src/types/api";

/** specs/admin-contest-management.spec.md — AC-CT-001..004, 006 trên giao diện /admin/contests. */

const HOUR = 3600_000;

function contest(id: number, status: ContestStatus, overrides: Partial<AdminContestDetail> = {}): AdminContestDetail {
  const start = status === "SCHEDULED" ? Date.now() + 24 * HOUR : Date.now() - HOUR;
  return {
    id,
    title: `Contest ${id}`,
    slug: `contest-${id}`,
    status,
    startTime: new Date(start).toISOString(),
    endTime: new Date(start + 2 * HOUR).toISOString(),
    scoringPolicy: "ACM_ICPC",
    maxViolationsAllowed: 3,
    registrationMode: "OPEN",
    createdBy: "teacher1",
    version: 0,
    hasSubmissions: false,
    problems: [],
    ...overrides,
  };
}

interface AdminMock {
  contests: Map<number, AdminContestDetail>;
  participants: Map<number, AdminParticipant[]>;
  requests: { method: string; path: string; body: any }[];
}

/** Mock trạng thái của /api/v1/admin/contests. Đăng ký SAU setupMockApi (route sau được ưu tiên). */
async function mockAdminContests(page: Page, seed: AdminContestDetail[] = []): Promise<AdminMock> {
  const m: AdminMock = { contests: new Map(seed.map((c) => [c.id, c])), participants: new Map(), requests: [] };
  let nextId = 500;
  const json = (route: Route, data: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });

  await page.route(/\/api\/v1\/admin\/contests/, async (route, request) => {
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData()!) : null;
    m.requests.push({ method, path, body });
    const idMatch = path.match(/\/admin\/contests\/(\d+)/);
    const id = idMatch ? Number(idMatch[1]) : null;

    if (path.endsWith("/admin/contests") && method === "GET") {
      const search = (url.searchParams.get("search") ?? "").toLowerCase();
      const status = url.searchParams.get("status");
      return json(
        route,
        Array.from(m.contests.values())
          .filter((c) => (!status || c.status === status) && c.title.toLowerCase().includes(search))
          .map((c) => ({
            id: c.id, title: c.title, slug: c.slug, status: c.status, startTime: c.startTime, endTime: c.endTime,
            registrationMode: c.registrationMode, problemCount: c.problems.length,
            participantCount: (m.participants.get(c.id) ?? []).length,
          }))
      );
    }
    if (path.endsWith("/admin/contests") && method === "POST") {
      const c = contest(nextId++, "SCHEDULED", {
        title: body.title, startTime: body.startTime, endTime: body.endTime, registrationMode: body.registrationMode,
        maxViolationsAllowed: body.maxViolationsAllowed,
        problems: body.problems.map((p: any) => ({
          problemId: p.problemId, slug: String(p.problemId), title: `P${p.problemId}`, difficulty: "EASY",
          label: p.label, orderIndex: p.orderIndex,
        })),
      });
      m.contests.set(c.id, c);
      return json(route, c, 201);
    }
    if (id !== null && path.endsWith("/participants") && method === "GET") {
      return json(route, m.participants.get(id) ?? []);
    }
    if (id !== null && path.endsWith("/participants") && method === "POST") {
      const known: Record<string, number> = { sv01: 11, sv02: 12 };
      const list = m.participants.get(id) ?? [];
      const lines = String(body.entries)
        .split(/[\n,;]+/)
        .map((s) => s.trim())
        .filter(Boolean)
        .map((input) => {
          const uid = known[input];
          if (!uid) return { input, outcome: "NOT_FOUND", username: null };
          if (list.some((p) => p.userId === uid)) return { input, outcome: "ALREADY_REGISTERED", username: input };
          list.push({
            userId: uid, username: input, email: `${input}@school.vn`, status: "REGISTERED", violationCount: 0,
            solved: 0, penaltyMinutes: 0, rank: null, hasSubmissions: false,
          });
          return { input, outcome: "ADDED", username: input };
        });
      m.participants.set(id, list);
      return json(route, { lines, added: lines.filter((l) => l.outcome === "ADDED").length });
    }
    if (id !== null && method === "GET") {
      const c = m.contests.get(id);
      return c ? json(route, c) : json(route, { status: 404, detail: "Contest not found" }, 404);
    }
    if (id !== null && method === "PUT") {
      const c = { ...m.contests.get(id)!, endTime: body.endTime, version: body.version + 1 };
      m.contests.set(id, c);
      return json(route, c);
    }
    if (id !== null && method === "DELETE") {
      m.contests.delete(id);
      return route.fulfill({ status: 204 });
    }
    return route.fallback();
  });
  return m;
}

async function open(page: Page, role: "ADMIN" | "INSTRUCTOR", seed: AdminContestDetail[] = []) {
  await installFullscreenMock(page);
  await setupMockApi(page);
  const m = await mockAdminContests(page, seed);
  await setMockAuthSession(page, { username: role === "ADMIN" ? "admin" : "teacher1", role });
  await page.goto("/admin/contests", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("button", { name: "New contest" })).toBeVisible();
  return m;
}

test.describe("Admin: contests", () => {
  test("AC-CT-001: instructor creates a contest with three published problems labelled A, B, C", async ({ page }) => {
    const m = await open(page, "INSTRUCTOR");
    await expect(page.getByText("No contests yet")).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Admin sections" }).getByRole("link", { name: "Contests" }))
      .toHaveAttribute("aria-current", "page");

    await page.getByRole("button", { name: "New contest" }).click();
    const editor = page.getByRole("dialog", { name: "Contest editor" });
    await editor.getByLabel("Title").fill("Giữa kỳ OOP");
    // Ô giờ hiện múi giờ của người xem.
    await expect(editor.getByText(/GMT[+-]\d+/).first()).toBeVisible();
    await expect(editor.getByRole("tab", { name: "Participants" })).toBeDisabled();

    await editor.getByRole("tab", { name: /Problems/ }).click();
    await editor.getByLabel("Add published problems").fill("Two");
    for (const t of ["Two Sum", "Add Two Numbers", "Median of Two Sorted Arrays"]) {
      await editor.getByRole("button", { name: `Add ${t}`, exact: true }).click();
    }
    await expect(editor.getByRole("button", { name: "Add Two Sum", exact: true })).toHaveText("Added");
    await expect(editor.getByLabel("Label for Two Sum")).toHaveValue("A");
    await expect(editor.getByLabel("Label for Add Two Numbers")).toHaveValue("B");
    await expect(editor.getByLabel("Label for Median of Two Sorted Arrays")).toHaveValue("C");

    // Đổi thứ tự: đưa bài C lên trên bài B.
    await editor.getByRole("button", { name: "Move Median of Two Sorted Arrays up" }).click();

    await editor.getByRole("button", { name: "Create contest" }).click();
    await expect(page.getByText("Contest created.")).toBeVisible();

    const post = m.requests.find((r) => r.method === "POST" && r.path.endsWith("/admin/contests"))!;
    expect(post.body.title).toBe("Giữa kỳ OOP");
    expect(post.body.startTime).toMatch(/Z$/);
    expect(Date.parse(post.body.endTime) - Date.parse(post.body.startTime)).toBe(2 * HOUR);
    expect(post.body.problems.map((p: any) => `${p.label}@${p.orderIndex}`)).toEqual(["A@0", "C@1", "B@2"]);

    // Trình soạn vẫn mở ở kỳ thi vừa tạo; tab thí sinh đã dùng được; danh sách có kỳ thi mới.
    await expect(editor.getByRole("tab", { name: "Participants" })).toBeEnabled();
    await editor.getByRole("button", { name: "Close editor" }).click();
    await expect(page.getByRole("button", { name: "Giữa kỳ OOP" })).toBeVisible();
  });

  test("AC-CT-003: end time not after start shows an inline error and blocks saving", async ({ page }) => {
    await open(page, "INSTRUCTOR");
    await page.getByRole("button", { name: "New contest" }).click();
    const editor = page.getByRole("dialog", { name: "Contest editor" });
    await editor.getByLabel("Title").fill("Bad times");
    const start = await editor.locator("#contest-start").inputValue();
    await editor.locator("#contest-end").fill(start);
    await expect(editor.getByRole("alert")).toHaveText("End time must be after start time");
    await expect(editor.getByRole("button", { name: "Create contest" })).toBeDisabled();
  });

  test("AC-CT-002: a running contest only lets staff extend the end time", async ({ page }) => {
    const running = contest(7, "RUNNING", {
      title: "Weekly Live",
      problems: [{ problemId: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY", label: "A", orderIndex: 0 }],
    });
    const m = await open(page, "INSTRUCTOR", [running]);
    await page.getByRole("button", { name: "Weekly Live" }).click();
    const editor = page.getByRole("dialog", { name: "Contest editor" });

    await expect(editor.getByRole("status")).toContainText("Only the end time can be extended");
    await expect(editor.getByLabel("Title")).toBeDisabled();
    await expect(editor.locator("#contest-start")).toBeDisabled();
    await expect(editor.getByLabel("Scoring")).toBeDisabled();
    await expect(editor.locator("#contest-end")).toBeEnabled();

    await editor.getByRole("tab", { name: /Problems/ }).click();
    await expect(editor.getByLabel("Add published problems")).toHaveCount(0);
    await expect(editor.getByRole("button", { name: "Remove Two Sum" })).toHaveCount(0);
    await editor.getByRole("tab", { name: "Details" }).click();

    // Lùi giờ kết thúc 30 phút.
    const end = new Date(Date.parse(running.endTime) + 30 * 60_000);
    const pad = (n: number) => String(n).padStart(2, "0");
    const local = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}`;
    await editor.locator("#contest-end").fill(local);
    await editor.getByRole("button", { name: "Save end time" }).click();
    await expect(page.getByText("Contest saved.")).toBeVisible();

    const put = m.requests.find((r) => r.method === "PUT")!;
    expect(put.body.startTime).toBe(running.startTime); // giờ bắt đầu gửi lại nguyên văn
    expect(put.body.version).toBe(0);
    expect(Math.abs(Date.parse(put.body.endTime) - end.getTime())).toBeLessThan(60_000);
    expect(put.body.problems).toEqual([{ problemId: 1, label: "A", orderIndex: 0 }]);
  });

  test("Completed contests are read-only", async ({ page }) => {
    await open(page, "INSTRUCTOR", [contest(8, "COMPLETED", { title: "Old one" })]);
    await page.getByRole("button", { name: "Old one" }).click();
    const editor = page.getByRole("dialog", { name: "Contest editor" });
    await expect(editor.getByRole("status")).toHaveText("This contest has ended. It is read-only.");
    await expect(editor.locator("#contest-end")).toBeDisabled();
    await expect(editor.getByRole("button", { name: /Save/ })).toHaveCount(0);
  });

  test("AC-CT-004: pasting a list reports added and not found per line", async ({ page }) => {
    const m = await open(page, "INSTRUCTOR", [contest(9, "SCHEDULED", { title: "Invite test", registrationMode: "INVITE_ONLY" })]);
    await page.getByRole("button", { name: "Invite test" }).click();
    const editor = page.getByRole("dialog", { name: "Contest editor" });
    await editor.getByRole("tab", { name: "Participants" }).click();
    await expect(editor.getByText("Nobody has registered yet.")).toBeVisible();
    await expect(editor.getByText(/This contest is invite-only/)).toBeVisible();

    await editor.getByLabel("Add participants").fill("sv01, sv02, khongtontai");
    await editor.getByRole("button", { name: "Add 3" }).click();

    const results = editor.getByRole("region", { name: "Add results" });
    await expect(results).toContainText("2 added · 1 not added");
    await expect(results.getByRole("listitem").nth(0)).toContainText("sv01added");
    await expect(results.getByRole("listitem").nth(2)).toContainText("khongtontainot found");
    // Dòng chưa thêm được giữ lại trong ô nhập để sửa.
    await expect(editor.getByLabel("Add participants")).toHaveValue("khongtontai");
    await expect(editor.getByRole("region", { name: "Participants" })).toContainText("2 participant(s)");
    expect(m.requests.filter((r) => r.method === "POST" && r.path.endsWith("/participants"))).toHaveLength(1);
  });

  test("AC-CT-004: student sees the invite-only message when registering", async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    const info = {
      id: 9, title: "Invite test", startTime: new Date(Date.now() + 24 * HOUR).toISOString(),
      endTime: new Date(Date.now() + 26 * HOUR).toISOString(), status: "SCHEDULED", scoringPolicy: "ACM_ICPC",
      maxViolationsAllowed: 3, problems: [],
    };
    await page.route(/\/api\/v1\/contests\/9(\/register)?$/, (route, request) =>
      request.method() === "POST"
        ? route.fulfill({
            status: 403, contentType: "application/problem+json",
            body: JSON.stringify({ status: 403, title: "Forbidden", detail: "This contest is invite-only" }),
          })
        : route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(info) })
    );
    await setMockAuthSession(page, { username: "sv03", role: "USER" });
    await page.goto("/exam/9", { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: /Register/ }).first().click();
    await expect(page.getByText("This contest is invite-only")).toBeVisible();
  });

  test("AC-CT-006: admin deletes a scheduled contest after confirming; instructor has no delete button", async ({ page }) => {
    const seed = [contest(10, "SCHEDULED", { title: "Delete me" }), contest(11, "RUNNING", { title: "Live one" })];
    const m = await open(page, "ADMIN", seed);
    await expect(page.getByRole("button", { name: "Delete Live one" })).toHaveCount(0); // chỉ kỳ SCHEDULED

    await page.getByRole("button", { name: "Delete Delete me" }).click();
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toContainText("Delete “Delete me”?");
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await dialog.getByRole("button", { name: "Delete contest" }).click();
    await expect(page.getByText("Contest deleted.")).toBeVisible();
    await expect(page.getByRole("button", { name: "Delete me", exact: true })).toHaveCount(0);
    expect(m.requests.filter((r) => r.method === "DELETE").map((r) => r.path)).toEqual(["/api/v1/admin/contests/10"]);
  });

  test("AC-CT-006: instructor does not see the delete button", async ({ page }) => {
    await open(page, "INSTRUCTOR", [contest(12, "SCHEDULED", { title: "Keep me" })]);
    await expect(page.getByRole("button", { name: "Keep me", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Delete Keep me" })).toHaveCount(0);
  });
});
