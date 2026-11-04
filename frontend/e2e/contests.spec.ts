import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";
import type { ContestInfo } from "../src/types/api";

const at = (minutes: number) => new Date(Date.now() + minutes * 60000).toISOString();
const problems = (n: number) =>
  Array.from({ length: n }, (_, i) => ({ id: i + 1, slug: `p-${i + 1}`, title: `Problem ${i + 1}`, difficulty: "EASY" as const, label: String.fromCharCode(65 + i) }));
const contest = (over: Partial<ContestInfo>): ContestInfo => ({
  id: 1,
  title: "Contest",
  startTime: at(-30),
  endTime: at(60),
  status: "RUNNING",
  scoringPolicy: "ACM_ICPC",
  maxViolationsAllowed: 3,
  problems: problems(4),
  ...over,
});

const CONTESTS: ContestInfo[] = [
  contest({ id: 21, title: "Live Round", status: "RUNNING", startTime: at(-30), endTime: at(60) }),
  contest({ id: 22, title: "Next Round", status: "SCHEDULED", startTime: at(60 * 24), endTime: at(60 * 24 + 90), problems: problems(3) }),
  contest({ id: 23, title: "Joined Round", status: "SCHEDULED", startTime: at(60 * 48), endTime: at(60 * 48 + 120), myStatus: "REGISTERED" }),
  contest({ id: 24, title: "Old Round", status: "COMPLETED", startTime: at(-60 * 72), endTime: at(-60 * 70), problems: problems(3), myStatus: "SUBMITTED", myRank: 4, mySolved: 2 }),
  contest({ id: 25, title: "Older Round", status: "COMPLETED", startTime: at(-60 * 240), endTime: at(-60 * 238), myStatus: "SUBMITTED", myRank: 12, mySolved: 3 }),
];

async function serveContests(page: Page, list: ContestInfo[] | "error") {
  await page.route(/\/api\/v1\/contests$/, (route) =>
    list === "error"
      ? route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) })
      : route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: list }) })
  );
}

test.describe("/exam contest list", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("shows live, upcoming and past contests with the user's record", async ({ page }) => {
    await serveContests(page, CONTESTS);
    await page.goto("/exam");

    const live = page.getByRole("region", { name: "Live Round" });
    await expect(live).toContainText("Live now");
    await expect(live).toContainText("4 problems · 90 minutes · Proctored");
    await expect(live.getByText(/^\d{2}:\d{2}:\d{2}$/)).toBeVisible();
    await expect(live.getByRole("button", { name: "Register & enter" })).toBeVisible();

    const upcoming = page.getByRole("region", { name: "Upcoming" });
    await expect(upcoming.getByRole("button", { name: "Register for Next Round" })).toBeEnabled();
    await expect(upcoming.getByRole("button", { name: "Registered for Joined Round" })).toBeDisabled();
    await expect(upcoming).toContainText("3 problems · 90 min");

    const past = page.getByRole("region", { name: "Past contests" });
    await expect(past.getByRole("link").first()).toContainText("Old Round");
    await expect(past.getByRole("link").first()).toContainText("#4");
    await expect(past.getByRole("link").first()).toContainText("2/3");
    await expect(past.getByRole("link").first()).toHaveAttribute("href", "/exam/24/leaderboard");

    const record = page.getByRole("complementary", { name: "Contest record and rules" });
    await expect(record).toContainText("3Contests joined");
    await expect(record).toContainText("#4Best rank");
  });

  test("Register uses the existing register API and marks the row registered", async ({ page }) => {
    await serveContests(page, CONTESTS);
    const calls: string[] = [];
    await page.route(/\/api\/v1\/contests\/\d+\/register$/, (route) => {
      calls.push(route.request().url());
      return route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: {} }) });
    });
    await page.goto("/exam");

    await page.getByRole("button", { name: "Register for Next Round" }).click();
    await expect(page.getByRole("button", { name: "Registered for Next Round" })).toBeDisabled();
    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatch(/\/contests\/22\/register$/);
  });

  test("Register & enter on a live contest registers, then opens the contest", async ({ page }) => {
    await serveContests(page, CONTESTS);
    await page.route(/\/api\/v1\/contests\/21\/register$/, (route) =>
      route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: {} }) })
    );
    await page.goto("/exam");
    await page.getByRole("button", { name: "Register & enter" }).click();
    await expect(page).toHaveURL(/\/exam\/21$/);
  });

  test("shows an empty state when there are no contests", async ({ page }) => {
    await serveContests(page, []);
    await page.goto("/exam");
    await expect(page.getByText("No contests yet")).toBeVisible();
  });

  test("shows an error with retry when the API fails", async ({ page }) => {
    await serveContests(page, "error");
    await page.goto("/exam");
    await expect(page.getByText("Couldn't load contests")).toBeVisible();
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
  });
});
