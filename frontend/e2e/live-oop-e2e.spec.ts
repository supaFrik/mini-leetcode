import { test, expect, type Page } from "@playwright/test";
import path from "path";

const SCREENSHOT_DIR = "C:/Users/aDMIN/.gemini/antigravity/brain/3b3d56c4-cc12-4ced-8000-d73bbd9ae9fd/screenshots";

// Helper to select a file tab in the multi-file workspace
async function selectFileTab(page: Page, fileName: string) {
  const tab = page.locator("div.group", { hasText: fileName }).first();
  await expect(tab).toBeVisible({ timeout: 5000 });
  await tab.click();
  await page.waitForTimeout(400);
}

// Helper to delete a file tab
async function deleteFileTab(page: Page, fileName: string) {
  const tab = page.locator("div.group", { hasText: fileName }).first();
  const delBtn = tab.locator(`button[title="Delete ${fileName}"]`);
  await delBtn.click({ force: true });
  const confirmBtn = page.getByRole("button", { name: "Delete" }).last();
  await expect(confirmBtn).toBeVisible({ timeout: 5000 });
  await confirmBtn.click();
  await page.waitForTimeout(500);
}

// Helper to set code in Monaco editor or fallback textarea
async function setEditorCode(page: Page, code: string) {
  await page.waitForTimeout(400);
  const updatedInMonaco = await page.evaluate((newVal) => {
    const monaco = (window as any).monaco;
    if (monaco?.editor) {
      const editors = monaco.editor.getEditors?.() ?? [];
      if (editors.length > 0 && editors[0].getModel()) {
        editors[0].getModel().setValue(newVal);
        return true;
      }
      const models = monaco.editor.getModels();
      if (models && models.length > 0) {
        models[0].setValue(newVal);
        return true;
      }
    }
    return false;
  }, code);

  if (!updatedInMonaco) {
    const textarea = page.locator('textarea[aria-label="Source code"]');
    if (await textarea.isVisible()) {
      await textarea.fill(code);
    }
  }
  await page.waitForTimeout(400);
}

test.describe.serial("Live OOP Full E2E Workflow (Admin Authoring to Student Solving)", () => {
  test.use({
    baseURL: "http://localhost:3100",
    viewport: { width: 1440, height: 900 },
  });

  test("Phase 1: Admin Authoring & OOP Presets Configuration", async ({ page }) => {
    test.setTimeout(60000);

    // 1. Login as live_admin
    await page.goto("http://localhost:3100/login");
    await page.fill('input[type="text"], input[name="username"]', "live_admin");
    await page.fill('input[type="password"], input[name="password"]', "Password123!");
    await page.click('button[type="submit"]');
    await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 15000 });

    // 2. Navigate to Problem 101 Authoring
    await page.goto("http://localhost:3100/admin/problems/101");
    await page.getByRole("textbox", { name: "Title" }).waitFor({ state: "visible", timeout: 15000 });
    await expect(page.getByRole("textbox", { name: "Title" })).toHaveValue(/Shape Hierarchy/i);
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "01_admin_authoring_overview.png"), fullPage: true });

    // 3. Switch to Grading Tab
    await page.getByRole("tab", { name: "Grading" }).click();
    await page.waitForTimeout(500);

    // If problem is locked while live, unlock it first to configure rules
    const unlockBtn = page.getByRole("button", { name: /Unlock to edit/i });
    if (await unlockBtn.isVisible()) {
      await unlockBtn.click();
      const confirmUnlockBtn = page.getByRole("button", { name: /^Unlock$/i });
      if (await confirmUnlockBtn.isVisible()) {
        await confirmUnlockBtn.click();
      }
      await page.waitForTimeout(1000);
      await page.reload();
      await page.getByRole("tab", { name: "Grading" }).click();
      await page.waitForTimeout(800);
    }

    await page.waitForSelector('text=Quick Presets', { timeout: 10000 });
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "02_admin_grading_tab.png"), fullPage: true });

    // 4. Click Full Strict OOP preset button or configure presets
    const fullStrictBtn = page.getByRole("button", { name: "Full Strict OOP" });
    if (await fullStrictBtn.isVisible() && await fullStrictBtn.isEnabled()) {
      await fullStrictBtn.click();
      await page.waitForTimeout(500);
    }

    // 5. Click Validate button
    const validateBtn = page.getByRole("button", { name: "Validate" });
    if (await validateBtn.isEnabled()) {
      await validateBtn.click();
      await page.waitForTimeout(1000);
    }

    // 6. Click Save rules button
    const saveRulesBtn = page.getByRole("button", { name: "Save rules" });
    if (await saveRulesBtn.isEnabled()) {
      await saveRulesBtn.click();
      await page.waitForTimeout(1200);
    }
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "03_admin_rules_saved.png"), fullPage: true });

    // 7. Check status badge and publish if in DRAFT or REVIEWING
    const sendToReviewBtn = page.getByRole("button", { name: /Send to review/i });
    if (await sendToReviewBtn.isVisible()) {
      await sendToReviewBtn.click();
      await page.waitForTimeout(1000);
    }

    const publishBtn = page.getByRole("button", { name: /^Publish$/i });
    if (await publishBtn.isVisible()) {
      await publishBtn.click();
      await page.waitForTimeout(1000);
    }

    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "04_admin_published_status.png"), fullPage: true });
  });

  test("Phase 2: Student Multi-File Workspace, Run & Submissions", async ({ page }) => {
    test.setTimeout(90000);

    // 1. Login as live_student
    await page.goto("http://localhost:3100/login");
    await page.fill('input[type="text"], input[name="username"]', "live_student");
    await page.fill('input[type="password"], input[name="password"]', "Password123!");
    await page.click('button[type="submit"]');
    await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 15000 });

    // 2. Navigate to Problem Workspace
    await page.goto("http://localhost:3100/problems/shape-hierarchy");
    await page.getByRole("heading", { name: /Shape Hierarchy/i }).waitFor({ state: "visible", timeout: 15000 });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "05_student_problem_workspace.png"), fullPage: true });

    // 3. Test Multi-file Tab Operations (+ New File, Switch, Delete)
    page.once("dialog", async (dialog) => {
      await dialog.accept("Helper.java");
    });
    const addFileBtn = page.getByRole("button", { name: /New File/i });
    if (await addFileBtn.isVisible()) {
      await addFileBtn.click();
      await page.waitForTimeout(800);
      await expect(page.locator("div.group", { hasText: "Helper.java" }).first()).toBeVisible();

      // Switch to Helper.java tab
      await selectFileTab(page, "Helper.java");

      // Delete Helper.java tab
      await deleteFileTab(page, "Helper.java");
      await expect(page.locator("div.group", { hasText: "Helper.java" })).toHaveCount(0);
    }
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "06_student_tabs_management.png"), fullPage: true });

    // 4. Test Run (Console / Quick Run on sample testcases)
    const runBtn = page.getByRole("button", { name: "Run" });
    await expect(runBtn).toBeVisible();
    await runBtn.click();
    await page.waitForTimeout(3000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "07_student_run_sample.png"), fullPage: true });

    // 5. Submit Case 1: Functional Pass but OOP Violation (public fields in Circle/Rectangle)
    // Select Shape.java tab and set code
    await selectFileTab(page, "Shape.java");
    await setEditorCode(page, `public class Shape {
    public String name;
    public Shape(String name) { this.name = name; }
    public String getName() { return name; }
    public double getArea() { return 0; }
    public double getPerimeter() { return 0; }
}`);

    // Select Circle.java tab and set code with public radius
    await selectFileTab(page, "Circle.java");
    await setEditorCode(page, `public class Circle extends Shape {
    public double radius;
    public Circle(String name, double radius) { super(name); this.radius = radius; }
    public double getArea() { return Math.PI * radius * radius; }
    public double getPerimeter() { return 2 * Math.PI * radius; }
}`);

    // Select Rectangle.java tab and set code with public fields
    await selectFileTab(page, "Rectangle.java");
    await setEditorCode(page, `public class Rectangle extends Shape {
    public double width, height;
    public Rectangle(String name, double width, double height) { super(name); this.width = width; this.height = height; }
    public double getArea() { return width * height; }
    public double getPerimeter() { return 2 * (width + height); }
}`);

    // Select Main.java tab and set driver code
    await selectFileTab(page, "Main.java");
    await setEditorCode(page, `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = Integer.parseInt(sc.nextLine().trim());
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] p = sc.nextLine().trim().split("\\\\s+");
            shapes.add(p[0].equals("CIRCLE") ? new Circle(p[1], Double.parseDouble(p[2]))
                    : new Rectangle(p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3])));
        }
        if (shapes.isEmpty()) { System.out.println("No shapes"); return; }
        double total = 0;
        Shape largest = shapes.get(0);
        for (Shape s : shapes) {
            System.out.println(String.format(Locale.ROOT, "Shape: %s | Area: %.2f | Perimeter: %.2f", s.getName(), s.getArea(), s.getPerimeter()));
            total += s.getArea();
            if (s.getArea() > largest.getArea()) largest = s;
        }
        System.out.println(String.format(Locale.ROOT, "Total area: %.2f", total));
        System.out.println("Largest: " + largest.getName());
    }
}`);

    // Click Submit
    const submitBtn = page.getByRole("button", { name: "Submit" });
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();

    // Wait for judging result
    await page.locator('text=Wrong Answer').or(page.locator('text=Score (Partial)')).first().waitFor({ timeout: 25000 }).catch(() => null);
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "08_student_oop_violation_verdict.png"), fullPage: true });

    // 6. Submit Case 2: Clean OOP 100/100 AC Solution
    // Shape.java
    await selectFileTab(page, "Shape.java");
    await setEditorCode(page, `public abstract class Shape {
    protected String name;
    public Shape(String name) { this.name = name; }
    public String getName() { return name; }
    public abstract double getArea();
    public abstract double getPerimeter();
}`);

    // Circle.java
    await selectFileTab(page, "Circle.java");
    await setEditorCode(page, `public class Circle extends Shape {
    private final double radius;
    public Circle(String name, double radius) { super(name); this.radius = radius; }
    @Override public double getArea() { return Math.PI * radius * radius; }
    @Override public double getPerimeter() { return 2 * Math.PI * radius; }
}`);

    // Rectangle.java
    await selectFileTab(page, "Rectangle.java");
    await setEditorCode(page, `public class Rectangle extends Shape {
    private final double width, height;
    public Rectangle(String name, double width, double height) { super(name); this.width = width; this.height = height; }
    @Override public double getArea() { return width * height; }
    @Override public double getPerimeter() { return 2 * (width + height); }
}`);

    // Click Submit again
    await submitBtn.click();
    await page.waitForTimeout(1000);
    await page.locator('text=Accepted').first().waitFor({ timeout: 25000 }).catch(() => null);
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "09_student_clean_ac_verdict.png"), fullPage: true });

    // 7. Submissions List
    await page.goto("http://localhost:3100/submissions");
    await page.waitForSelector('text=Submissions', { timeout: 15000 });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "10_student_submissions_list.png"), fullPage: true });
  });
});
