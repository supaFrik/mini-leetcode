// Live UI test of the admin problem editor on http://localhost:3100 (real backend + judge sandbox).
import { createRequire } from "node:module";
const require = createRequire(process.env.FRONTEND_DIR + "/package.json");
const { chromium, expect } = require("@playwright/test");

const BASE = process.env.BASE ?? "http://localhost:3100";
const SHOTS = process.env.SHOTS;
const ts = Date.now().toString(36);
const TITLE = `ZZ UI Smoke ${ts}`;
const MAIN = `import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.print("Enter n: ");
        System.out.flush();
        System.out.println(in.nextInt() * 2);
    }
}
`;
const results = [];
let n = 0;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const consoleErrors = [];
page.on("pageerror", (e) => consoleErrors.push(String(e)));
const shot = (name) => page.screenshot({ path: `${SHOTS}/${String(++n).padStart(2, "0")}-${name}.png` });
async function step(name, fn) {
  try {
    await fn();
    results.push([name, true]);
    console.log(`PASS  ${name}`);
  } catch (e) {
    results.push([name, false]);
    console.log(`FAIL  ${name}\n      ${String(e.message).split("\n").slice(0, 4).join("\n      ")}`);
    await shot(`FAIL-${name.replace(/\W+/g, "-")}`).catch(() => {});
  }
}
const row = () => page.locator("div.grid", { has: page.getByRole("button", { name: new RegExp(`^${TITLE}`) }) }).first();
const editor = () => page.getByRole("dialog", { name: "Problem editor" });

try {
  await step("login as devadmin", async () => {
    await page.goto(`${BASE}/login`);
    await page.getByLabel("Username").fill("devadmin");
    await page.getByLabel("Password", { exact: true }).fill(process.env.ADMIN_PW);
    await page.getByLabel("Password", { exact: true }).press("Enter");
    await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 15000 });
    await expect(page.getByRole("link", { name: "Admin" })).toBeVisible();
  });

  await step("open admin problems page", async () => {
    await page.goto(`${BASE}/admin/problems`);
    await expect(page.getByRole("region", { name: "Problem administration" })).toBeVisible();
    await expect(page.getByLabel("Search problems")).toBeVisible();
    await shot("admin-list");
  });

  await step("filters: status chip + search", async () => {
    await page.getByLabel("Filter by lifecycle status").selectOption("PUBLISHED");
    // Danh sách tải lại bất đồng bộ — chờ tới khi không còn dòng nào khác PUBLISHED (tối đa 10 s).
    const table = page.getByRole("region", { name: "Problem administration" });
    await expect.poll(async () => table.getByText(/^(Draft|Reviewing|Archived)$/i).count(), { timeout: 10000 }).toBe(0);
    await expect(table.getByText(/^Published$/i).first()).toBeVisible();
    await page.getByLabel("Filter by lifecycle status").selectOption("");
  });

  await step("create OOP problem → editor stays open", async () => {
    await page.getByRole("button", { name: /New problem/ }).click();
    await expect(editor().getByRole("tab", { name: "Test cases" })).toBeDisabled();
    await editor().getByLabel("Title").fill(TITLE);
    await editor().getByLabel("Description (Markdown)").fill("Read **n** from stdin, print `2n`.");
    await editor().getByLabel("Problem Type").selectOption("OOP_GRINDING");
    await expect(editor().getByText("Starter templates (.java)")).toBeVisible();
    await shot("create-form");
    await editor().getByRole("button", { name: "Create" }).click();
    await expect(page.getByText("Problem created.")).toBeVisible();
    await expect(editor().getByText("Edit problem")).toBeVisible();
    await expect(editor().getByRole("tab", { name: "Test cases" })).toBeEnabled();
  });

  await step("reference solution: write + save", async () => {
    await editor().getByRole("tab", { name: "Reference & console" }).click();
    await editor().getByRole("button", { name: "Start with Main.java" }).click();
    await editor().getByLabel("Contents of Main.java").fill(MAIN);
    await expect(editor().getByText("Unsaved changes")).toBeVisible();
    await editor().getByRole("button", { name: "Save reference" }).click();
    await expect(page.getByText("Reference solution saved.")).toBeVisible();
  });

  await step("console: run reference, type 21 → 42", async () => {
    await editor().getByRole("button", { name: "Run", exact: true }).click();
    const log = editor().getByRole("log", { name: "Console output" });
    await expect(log).toContainText("Enter n:", { timeout: 60000 });
    await editor().getByLabel("Program input").fill("21");
    await editor().getByLabel("Program input").press("Enter");
    await expect(log).toContainText("42", { timeout: 30000 });
    await shot("console");
  });

  await step("grading: invalid JSON shows path errors", async () => {
    await editor().getByRole("tab", { name: "Grading" }).click();
    await editor().getByLabel(/grading_config/).fill('{"rules":[{"targetClassName":"Main","expectedTyp":"CLASS"}]}');
    await editor().getByRole("button", { name: "Validate" }).click();
    await expect(editor().getByRole("alert")).toContainText("rules[0].expectedTyp: unknown key");
    await shot("grading-error");
  });

  await step("grading: example → validate → save", async () => {
    await editor().getByLabel(/grading_config/).fill("");
    await editor().getByRole("button", { name: "Insert example" }).click();
    await editor().getByRole("button", { name: "Validate" }).click();
    await expect(editor().getByText(/Valid — 1 rule/)).toBeVisible();
    await editor().getByRole("button", { name: "Save rules" }).click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();
    await shot("grading-saved");
  });

  await step("test case: add with Fill from reference (public)", async () => {
    await editor().getByRole("tab", { name: "Test cases" }).click();
    await expect(editor().getByText("No test cases yet")).toBeVisible();
    await editor().getByRole("button", { name: "Add case" }).click();
    await editor().getByLabel("Input (stdin)").fill("21\n");
    await editor().getByRole("button", { name: "Fill from reference" }).click();
    await expect(editor().getByLabel("Expected output (stdout)")).toHaveValue("Enter n: 42\n", { timeout: 60000 });
    await editor().getByLabel(/Hidden/).uncheck();
    await editor().getByRole("button", { name: "Save case" }).click();
    await expect(page.getByText("Test case added.")).toBeVisible();
  });

  await step("test case: add hidden case with wrong output", async () => {
    await editor().getByRole("button", { name: "Add case" }).click();
    await editor().getByLabel("Input (stdin)").fill("5\n");
    await editor().getByLabel("Expected output (stdout)").fill("11\n");
    await editor().getByRole("button", { name: "Save case" }).click();
    await expect(page.getByText("Test case added.").last()).toBeVisible();
    await expect(editor().getByText("#2")).toBeVisible();
  });

  await step("check against reference → 1 match, 1 differ", async () => {
    await editor().getByRole("button", { name: "Check against reference" }).click();
    await expect(editor().getByText(/1\s*match · 1\s*differ · 0\s*failed/)).toBeVisible({ timeout: 90000 });
    await shot("check");
  });

  await step("apply reference output → case #2 fixed", async () => {
    await editor().getByRole("button", { name: /Use reference output for 1 case/ }).click();
    await expect(page.getByText("Updated 1 expected output.")).toBeVisible({ timeout: 90000 });
    await expect(editor().getByText("Enter n: 10")).toBeVisible();
  });

  await step("edit test case → toggle hidden", async () => {
    await editor().getByRole("button", { name: "Edit test case #2" }).click();
    await editor().getByLabel(/Hidden/).uncheck();
    await editor().getByRole("button", { name: "Save case" }).click();
    await expect(page.getByText("Test case updated.")).toBeVisible();
  });

  await step("details: rename + save (closes editor)", async () => {
    await editor().getByRole("tab", { name: "Details" }).click();
    await editor().getByLabel("Title").fill(`${TITLE} v2`);
    await editor().getByRole("button", { name: "Save" }).click();
    await expect(page.getByText("Problem updated.")).toBeVisible();
    await expect(editor()).toHaveCount(0);
    await page.getByLabel("Search problems").fill(TITLE);
    await expect(row()).toContainText("v2");
  });

  await step("lifecycle: Reviewing → Publish", async () => {
    await row().getByRole("button", { name: "Reviewing" }).click();
    await expect(row().getByRole("button", { name: "Publish" })).toBeVisible();
    await row().getByRole("button", { name: "Publish" }).click();
    await expect(row().getByRole("button", { name: "Archive" })).toBeVisible();
    await shot("published");
  });

  await step("published: test cases + grading locked in editor", async () => {
    await row().getByRole("button", { name: new RegExp(`^${TITLE}`) }).click();
    await editor().getByRole("tab", { name: "Test cases" }).click();
    await expect(editor().getByText(/its test cases are locked/)).toBeVisible();
    await expect(editor().getByRole("button", { name: "Add case" })).toHaveCount(0);
    await editor().getByRole("tab", { name: "Grading" }).click();
    await expect(editor().getByText(/its grading rules are locked/)).toBeVisible();
    await expect(editor().getByRole("button", { name: "Save rules" })).toHaveCount(0);
    await shot("locked");
    await editor().getByRole("button", { name: "Close editor" }).click();
  });

  await step("student view: problem page loads", async () => {
    const slugLink = row().getByRole("button", { name: new RegExp(`^View ${TITLE}`) });
    await slugLink.click();
    await expect(page.getByText(`${TITLE} v2`).first()).toBeVisible();
    await shot("problem-page");
    await page.goto(`${BASE}/admin/problems`);
    await page.getByLabel("Search problems").fill(TITLE);
  });

  await step("lifecycle: Archive → Draft", async () => {
    await row().getByRole("button", { name: "Archive" }).click();
    await expect(row().getByRole("button", { name: "Draft" })).toBeVisible();
    await row().getByRole("button", { name: "Draft" }).click();
    await expect(row().getByRole("button", { name: "Reviewing" })).toBeVisible();
  });
} finally {
  await step("delete problem (cleanup)", async () => {
    await page.goto(`${BASE}/admin/problems`);
    await page.getByLabel("Search problems").fill(TITLE);
    await row().getByRole("button", { name: new RegExp(`^Delete ${TITLE}`) }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Delete problem" }).click();
    await expect(page.getByText("Problem deleted.")).toBeVisible();
    await expect(row()).toHaveCount(0);
  });
  const failed = results.filter(([, ok]) => !ok).length;
  console.log(`\nTOTAL ${results.length}  PASS ${results.length - failed}  FAIL ${failed}`);
  console.log(`PAGE ERRORS: ${consoleErrors.length ? consoleErrors.join(" | ") : "none"}`);
  await browser.close();
}
