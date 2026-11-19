import { test, expect } from "@playwright/test";
import path from "path";

const SCREENSHOT_DIR = "C:/Users/aDMIN/.gemini/antigravity/brain/3b3d56c4-cc12-4ced-8000-d73bbd9ae9fd/screenshots";

test("Capture finished submissions and submissions list", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 950 });

  // 1. Log in via UI
  await page.goto("http://localhost:3100/login");
  await page.fill('input[type="text"], input[name="username"]', "learner_2616");
  await page.fill('input[type="password"], input[name="password"]', "Password123!");
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 10000 });

  // 2. Submissions list
  await page.goto("http://localhost:3100/submissions");
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, "13_user_submissions_list.png"), fullPage: true });

  // 3. Visit Submission 189 (Accepted - Clean OOP 100/100)
  await page.goto("http://localhost:3100/submissions/189");
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, "12_user_submission_clean_oop_100.png"), fullPage: true });

  // 4. Visit Submission 188 (Wrong Answer - OOP Violation 60/100)
  await page.goto("http://localhost:3100/submissions/188");
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, "11_user_submission_oop_violation_60.png"), fullPage: true });
});
