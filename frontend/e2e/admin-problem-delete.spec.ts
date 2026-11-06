import { test, expect, type Page } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/** AC-SF-001..003 (specs/admin-small-fixes.spec.md): xoá bài phải xác nhận; chỉ ADMIN thấy nút xoá. */

const draft: ProblemFull = {
  id: 88, slug: "zz-delete-me", title: "ZZ Delete Me", difficulty: "EASY", tags: [],
  description: "x", timeLimitMs: 1000, memoryLimitMb: 256, status: "DRAFT", type: "ALGORITHMIC",
};

async function trackDeletes(page: Page, status = 204) {
  const deletes: string[] = [];
  await page.route(/\/api\/v1\/problems\/\d+$/, async (route, request) => {
    if (request.method() !== "DELETE") return route.fallback();
    deletes.push(new URL(request.url()).pathname);
    if (status === 204) return route.fulfill({ status: 204 });
    return route.fulfill({
      status, contentType: "application/json",
      body: JSON.stringify({ status, title: "Conflict", detail: "Problem has submissions and cannot be deleted" }),
    });
  });
  return deletes;
}

/** Route DELETE phải đăng ký SAU mock chung (Playwright chạy route đăng ký sau trước). */
async function openAdmin(page: Page, role: "ADMIN" | "INSTRUCTOR", deleteStatus?: number) {
  await installFullscreenMock(page);
  const mock = await setupMockApi(page);
  mock.addProblem(draft);
  const deletes = deleteStatus === undefined ? [] : await trackDeletes(page, deleteStatus);
  await setMockAuthSession(page, { username: role === "ADMIN" ? "admin" : "teacher1", role });
  await page.goto("/admin/problems", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("button", { name: /^ZZ Delete Me/ })).toBeVisible();
  return deletes;
}

test.describe("Admin: delete problem", () => {
  test("Cancel (and Esc) keeps the problem and sends no request", async ({ page }) => {
    const deletes = await openAdmin(page, "ADMIN", 204);
    await page.getByRole("button", { name: "Delete ZZ Delete Me" }).click();
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toContainText("Delete “ZZ Delete Me”?");
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await expect(dialog).toHaveCount(0);

    await page.getByRole("button", { name: "Delete ZZ Delete Me" }).click();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("alertdialog")).toHaveCount(0);
    await expect(page.getByRole("button", { name: /^ZZ Delete Me/ })).toBeVisible();
    expect(deletes).toEqual([]);
  });

  test("Confirm deletes the problem", async ({ page }) => {
    const deletes = await openAdmin(page, "ADMIN", 204);
    await page.getByRole("button", { name: "Delete ZZ Delete Me" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Delete problem" }).click();
    await expect(page.getByText("Problem deleted.")).toBeVisible();
    expect(deletes).toEqual([`/api/v1/problems/${draft.id}`]);
  });

  test("Server conflict is shown and the dialog closes", async ({ page }) => {
    await openAdmin(page, "ADMIN", 409);
    await page.getByRole("button", { name: "Delete ZZ Delete Me" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Delete problem" }).click();
    await expect(page.getByRole("alertdialog")).toHaveCount(0);
    await expect(page.getByText("Problem has submissions and cannot be deleted")).toBeVisible();
  });

  test("Instructors do not see the delete button", async ({ page }) => {
    await openAdmin(page, "INSTRUCTOR");
    await expect(page.getByRole("button", { name: "Delete ZZ Delete Me" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "View ZZ Delete Me" })).toBeVisible();
  });
});
