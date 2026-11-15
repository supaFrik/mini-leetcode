import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/** Hợp đồng y như StudyPlanController / PersonalAccessTokenController (JSON trần, không bọc ApiResponse). */
const stage = (n: number, status: string, published = true) => ({
  stageNo: n, problemId: 100 + n, slug: `mini-bank-stage-${n}`, title: `Mini Bank ${n}/5`,
  difficulty: "EASY", problemType: "OOP_GRINDING", published, status,
});
const PLAN = {
  id: 1, slug: "oop-voi-java", title: "OOP với Java", description: "Lộ trình OOP", status: "PUBLISHED",
  stageCount: 4, doneStages: 1,
  items: [
    { id: 1, position: 0, kind: "PROBLEM", title: "Luyện: lớp & đóng gói", done: true,
      stages: [{ ...stage(1, "DONE"), slug: "bank-account-class", title: "Bank Account Class", problemType: "ALGORITHMIC" }] },
    { id: 2, position: 1, kind: "PROJECT", title: "Project: Mini Bank Console", done: false,
      stages: [stage(1, "IN_PROGRESS"), stage(2, "LOCKED"), stage(3, "NEVER", false)] },
  ],
};

async function serveStudyPlans(page: Page) {
  await page.route(/\/api\/v1\/study-plans(\/.*)?$/, (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/study-plans") {
      const { items, ...summary } = PLAN;
      return route.fulfill({ contentType: "application/json", body: JSON.stringify([{ ...summary, itemCount: items.length }]) });
    }
    if (path === "/api/v1/study-plans/oop-voi-java") {
      return route.fulfill({ contentType: "application/json", body: JSON.stringify(PLAN) });
    }
    return route.fulfill({ status: 404, contentType: "application/problem+json", body: JSON.stringify({ status: 404, detail: "Study plan not found" }) });
  });
}

/** Token server giả: chỉ trả token gốc lúc tạo, danh sách không bao giờ có trường token. */
async function serveTokens(page: Page) {
  const tokens: Array<Record<string, unknown>> = [];
  await page.route(/\/api\/v1\/me\/tokens(\/\d+)?$/, async (route) => {
    const req = route.request();
    const id = Number(new URL(req.url()).pathname.split("/").pop());
    if (req.method() === "POST") {
      const body = JSON.parse(req.postData() || "{}");
      const info = { id: tokens.length + 1, name: body.name, createdAt: new Date().toISOString(), revoked: false,
        expiresAt: new Date(Date.now() + body.expiresInDays * 86400000).toISOString(), lastUsedAt: null };
      tokens.push(info);
      return route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify({ token: "mlc_pat_e2eSecretValue", info }) });
    }
    if (req.method() === "DELETE") {
      const t = tokens.find((x) => x.id === id);
      if (t) t.revoked = true;
      return route.fulfill({ status: 204, body: "" });
    }
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(tokens) });
  });
}

test.describe("/study-plans", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem({ id: 101, slug: "mini-bank-stage-1", title: "Mini Bank 1/5", difficulty: "EASY", tags: [], type: "OOP_GRINDING",
      description: "Create the **Account** class.", timeLimitMs: 2000, memoryLimitMb: 256, status: "PUBLISHED" });
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await serveStudyPlans(page);
  });

  test("lists plans with progress and links to the plan page", async ({ page }) => {
    await page.goto("/study-plans");

    const card = page.getByRole("link", { name: /OOP với Java/ });
    await expect(card).toContainText("2 items · 4 stages");
    await expect(card).toContainText("1/4 stages · 25%");
    await card.click();
    await expect(page).toHaveURL(/\/study-plans\/oop-voi-java$/);
    await expect(page.getByRole("heading", { name: "OOP với Java", level: 1 })).toBeVisible();
  });

  test("open stages link to their stage page; locked and maintenance stages do not", async ({ page }) => {
    await page.goto("/study-plans/oop-voi-java");

    const project = page.getByRole("region", { name: "Project: Mini Bank Console" });
    await expect(project.getByRole("link", { name: /Stage 1/ })).toHaveAttribute("href", "/study-plans/oop-voi-java/2/1");
    await expect(project.getByText("Finish stage 1 first")).toBeVisible();
    await expect(project.getByText("Being updated — try later")).toBeVisible();
    await expect(project.getByRole("link")).toHaveCount(1);

    const practice = page.getByRole("region", { name: "Luyện: lớp & đóng gói" });
    await expect(practice.getByText("Completed")).toBeVisible();
    await expect(page.getByRole("link", { name: "Set up the plugin →" })).toHaveAttribute("href", "/ide");
  });

  test("AC-4: a project stage page opens in IntelliJ — install, connect once, then open", async ({ page }) => {
    await serveTokens(page);
    const ide = { up: false, connected: false, connects: [] as Record<string, string>[], opens: [] as Record<string, unknown>[] };
    await page.route(/^http:\/\/127\.0\.0\.1:633\d\d\/api\/mini-leetcode/, (route) => {
      const url = new URL(route.request().url());
      if (!ide.up || url.port !== "63342") return route.abort("connectionrefused");
      const action = url.searchParams.get("action");
      const body = route.request().postData() ? JSON.parse(route.request().postData()!) : {};
      const reply = (data: unknown, status = 200) => route.fulfill({ status, contentType: "application/json",
        headers: { "Access-Control-Allow-Origin": route.request().headers()["origin"] ?? "*" }, body: JSON.stringify(data) });
      if (action === "ping") return reply({ version: "0.1.0", connected: ide.connected });
      if (action === "connect") { ide.connects.push(body); ide.connected = true; return reply({ connected: true }); }
      if (action === "open") { ide.opens.push(body); return reply({ accepted: true }, 202); }
      return reply({ error: "unknown" }, 400);
    });

    await page.goto("/study-plans/oop-voi-java/2/1");
    await expect(page.getByRole("heading", { name: "Project: Mini Bank Console. Stage 1/3", level: 1 })).toBeVisible();
    const stage = page.getByRole("region", { name: "Stage" });
    await expect(stage.getByRole("link", { name: "Install the IntelliJ plugin" })).toHaveAttribute("href", "/ide");

    ide.up = true;
    await stage.getByRole("button", { name: "check again" }).click();
    await stage.getByRole("button", { name: "Connect IntelliJ" }).click();
    await expect(stage.getByText("IntelliJ connected")).toBeVisible();
    expect(ide.connects).toEqual([{ serverUrl: new URL(page.url()).origin, token: "mlc_pat_e2eSecretValue" }]);

    await stage.getByRole("button", { name: "Open in IntelliJ" }).click();
    await expect.poll(() => ide.opens).toEqual([{ planSlug: "oop-voi-java", itemId: 2, stageNo: 1 }]);
    await expect(stage.getByRole("link", { name: /Solve it in the web workspace/ })).toHaveAttribute("href", "/problems/mini-bank-stage-1");
  });

  test("a locked stage page explains what to finish first", async ({ page }) => {
    await page.goto("/study-plans/oop-voi-java/2/2");
    await expect(page.getByRole("region", { name: "Stage" })).toContainText("Finish stage 1 first.");
    await expect(page.getByRole("button", { name: /IntelliJ/ })).toHaveCount(0);
  });

  test("unknown plan shows an error with retry", async ({ page }) => {
    await page.goto("/study-plans/nope");
    // Next.js có sẵn một role="alert" (route announcer): lọc theo nội dung.
    await expect(page.getByRole("alert").filter({ hasText: "Couldn't load this study plan" })).toContainText("Study plan not found");
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
  });

  test("header links to study plans and the IntelliJ plugin page", async ({ page }) => {
    await page.goto("/study-plans");
    await expect(page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Study Plans" })).toHaveAttribute("aria-current", "page");
  });
});

test.describe("/ide", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await serveTokens(page);
  });

  test("creates a token shown once, lists it without the secret, and revokes it", async ({ page }) => {
    await page.goto("/ide");
    await expect(page.getByRole("link", { name: /mini-leetcode-intellij\.zip/ })).toHaveAttribute("href", "/downloads/mini-leetcode-intellij.zip");

    await page.getByLabel("Name").fill("Laptop");
    await page.getByLabel("Expires in").selectOption("30");
    await page.getByRole("button", { name: "Create token" }).click();

    await expect(page.getByTestId("new-token")).toHaveText("mlc_pat_e2eSecretValue");
    const list = page.getByLabel("Your tokens");
    await expect(list).toContainText("Laptop");
    await expect(list).not.toContainText("mlc_pat_");

    await list.getByRole("button", { name: "Revoke Laptop" }).click();
    await expect(list).toContainText("revoked");
    await expect(list.getByRole("button", { name: "Revoke Laptop" })).toHaveCount(0);
  });
});
