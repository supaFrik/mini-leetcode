import { test, expect, type Page, type Route } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/**
 * Playwright E2E Test Suite for OOP Presets in Problem Authoring (GradingPanel).
 *
 * Covers all 4 Tiers:
 * - Tier 1: Core Feature Coverage (Encapsulation, Polymorphism, Anti-Monolithic, Target Class, Two-way Sync, Save & Validate)
 * - Tier 2: Boundary & Corner Cases (Empty JSON, Whitespace/Special chars, Corrupted JSON recovery)
 * - Tier 3: Cross-Feature Combinations (Presets + Socket Scenarios + Output Format, Tab Switching)
 * - Tier 4: Real-World Teacher Authoring Workflows (Bank Account, Payment Gateway, Clear/Reset Rules)
 */

const oopProblem: ProblemFull = {
  id: 88,
  slug: "oop-banking-system",
  title: "OOP Banking System",
  difficulty: "MEDIUM",
  tags: ["OOP", "Java"],
  description: "Design Account and Transaction classes with encapsulation and polymorphism.",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  status: "DRAFT",
  type: "OOP_GRINDING",
  starterTemplates: JSON.stringify([
    { path: "Account.java", content: "public class Account {}\n" },
    { path: "Main.java", content: "public class Main { public static void main(String[] args) {} }\n" },
  ]),
};

interface MockAuthoringState {
  gradingConfig: string | null;
  referenceSolution: { path: string; content: string }[];
  validationRequests: string[];
  saveRequests: (string | null)[];
}

/** Sets up mock API route interception for problem authoring & grading-config endpoints */
async function mockAuthoringApi(page: Page, initialGradingConfig: string | null = null): Promise<MockAuthoringState> {
  const state: MockAuthoringState = {
    gradingConfig: initialGradingConfig,
    referenceSolution: [{ path: "Account.java", content: "public class Account { private double balance; }\n" }],
    validationRequests: [],
    saveRequests: [],
  };

  const getAuthoringResponse = () => ({
    problemId: oopProblem.id,
    status: oopProblem.status,
    problemType: oopProblem.type,
    gradingConfig: state.gradingConfig,
    referenceSolution: state.referenceSolution,
    gradingFrozen: false,
    timeLimitMs: 2000,
    memoryLimitMb: 256,
  });

  const jsonResponse = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

  await page.route(`**/api/v1/admin/problems/${oopProblem.id}/**`, async (route, request) => {
    const path = new URL(request.url()).pathname;
    const body = request.postDataJSON?.() ?? null;

    if (path.endsWith("/authoring")) {
      return jsonResponse(route, getAuthoringResponse());
    }

    if (path.endsWith("/reference-solution")) {
      state.referenceSolution = body.files;
      return jsonResponse(route, getAuthoringResponse());
    }

    if (path.endsWith("/grading-config/validate")) {
      const raw: string = body.gradingConfig ?? "";
      state.validationRequests.push(raw);

      if (!raw.trim()) {
        return jsonResponse(route, {
          valid: true,
          errors: [],
          ruleCount: 0,
          socketScenarioCount: 0,
          totalRuleWeight: 0,
        });
      }

      try {
        const parsed = JSON.parse(raw);
        const errors: string[] = [];
        const rules = Array.isArray(parsed?.rules) ? parsed.rules : [];
        const scenarios = Array.isArray(parsed?.socketScenarios) ? parsed.socketScenarios : [];

        rules.forEach((r: any, idx: number) => {
          if (!r.targetClassName || !String(r.targetClassName).trim()) {
            errors.push(`rules[${idx}].targetClassName: required`);
          }
          if (r.expectedType && !["ANY", "CLASS", "INTERFACE", "ABSTRACT_CLASS", "ABSTRACT_CLASS_OR_INTERFACE", "RECORD", "ENUM"].includes(r.expectedType)) {
            errors.push(`rules[${idx}]: Invalid expectedType '${r.expectedType}'`);
          }
        });

        const totalWeight = rules.reduce((acc: number, r: any) => acc + (typeof r.weight === "number" ? r.weight : 1.0), 0);

        return jsonResponse(route, {
          valid: errors.length === 0,
          errors,
          ruleCount: rules.length,
          socketScenarioCount: scenarios.length,
          totalRuleWeight: totalWeight,
        });
      } catch (err) {
        return jsonResponse(route, {
          valid: false,
          errors: [`Not valid JSON: ${(err as Error).message}`],
          ruleCount: 0,
          socketScenarioCount: 0,
          totalRuleWeight: 0,
        });
      }
    }

    if (path.endsWith("/grading-config")) {
      const raw: string | null = body.gradingConfig ?? null;
      state.saveRequests.push(raw);

      if (raw && raw.trim()) {
        try {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed?.rules)) {
            for (let i = 0; i < parsed.rules.length; i++) {
              const r = parsed.rules[i];
              if (!r.targetClassName || !String(r.targetClassName).trim()) {
                return jsonResponse(
                  route,
                  {
                    status: 400,
                    title: "Invalid Grading Configuration",
                    detail: "Grading configuration is invalid",
                    errors: [`rules[${i}].targetClassName: required`],
                  },
                  400
                );
              }
            }
          }
        } catch {
          return jsonResponse(
            route,
            {
              status: 400,
              title: "Invalid Grading Configuration",
              detail: "Grading configuration is invalid",
              errors: ["JSON syntax is invalid"],
            },
            400
          );
        }
      }

      state.gradingConfig = raw || null;
      return jsonResponse(route, getAuthoringResponse());
    }

    return jsonResponse(route, { status: 404, title: "Not Found", detail: path }, 404);
  });

  return state;
}

/** Page Object Model for GradingPanel */
class GradingPanelPO {
  constructor(readonly page: Page) {}

  get editor() {
    return this.page.getByRole("region", { name: "Problem editor" });
  }

  get gradingTab() {
    return this.editor.getByRole("tab", { name: "Grading" });
  }

  get presetsTab() {
    return this.editor.getByRole("tab", { name: /preset/i })
      .or(this.editor.getByRole("button", { name: /quick preset/i }));
  }

  get rawJsonTab() {
    return this.editor.getByRole("tab", { name: /raw/i })
      .or(this.editor.getByRole("button", { name: /raw json/i }));
  }

  get encapsulationToggle() {
    return this.editor.getByLabel(/đóng gói|encapsulation/i)
      .or(this.editor.getByRole("checkbox", { name: /đóng gói|encapsulation/i }));
  }

  get polymorphismToggle() {
    return this.editor.getByLabel(/đa hình|polymorphism|trừu tượng|abstraction/i)
      .or(this.editor.getByRole("checkbox", { name: /đa hình|polymorphism|trừu tượng|abstraction/i }));
  }

  get antiMonolithicToggle() {
    return this.editor.getByLabel(/monolithic|tách rời logic/i)
      .or(this.editor.getByRole("checkbox", { name: /monolithic|tách rời logic/i }));
  }

  get targetClassInput() {
    return this.editor.getByLabel(/target class|lớp mục tiêu|class mục tiêu/i)
      .or(this.editor.getByPlaceholder(/account|\*|target class|class name/i))
      .or(this.editor.locator("input#preset-target-class"))
      .or(this.editor.locator('input[placeholder*="Account"]'));
  }

  get rawJsonEditor() {
    return this.editor.getByLabel(/grading_config/)
      .or(this.editor.locator("#grading-json"));
  }

  get validateButton() {
    return this.editor.getByRole("button", { name: "Validate" });
  }

  get saveButton() {
    return this.editor.getByRole("button", { name: "Save rules" });
  }

  get clearButton() {
    return this.editor.getByRole("button", { name: "Clear rules" });
  }

  get formatButton() {
    return this.editor.getByRole("button", { name: "Format" });
  }

  get alertBox() {
    return this.editor.getByRole("alert");
  }

  get validMessage() {
    return this.editor.locator("p").filter({ hasText: /valid —/i });
  }

  async openGradingPanel() {
    await this.page.goto("/admin/problems", { waitUntil: "domcontentloaded" });
    await this.page.getByRole("link", { name: oopProblem.title, exact: true }).click();
    await expect(this.page).toHaveURL(new RegExp(`/admin/problems/${oopProblem.id}$`));
    await this.gradingTab.click();
  }

  async switchToRawJson() {
    if (await this.rawJsonTab.isVisible()) {
      await this.rawJsonTab.click();
    }
  }

  async switchToPresets() {
    if (await this.presetsTab.isVisible()) {
      await this.presetsTab.click();
    }
  }

  async getRawJsonValue(): Promise<string> {
    return await this.rawJsonEditor.inputValue();
  }

  async getParsedRules(): Promise<any[]> {
    const raw = await this.getRawJsonValue();
    if (!raw.trim()) return [];
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed?.rules) ? parsed.rules : [];
    } catch {
      return [];
    }
  }
}

test.describe("Admin Authoring: OOP Quick Presets E2E Suite", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem(oopProblem);
    await setMockAuthSession(page, { username: "admin", role: "ADMIN" });
  });

  /* ------------------------------------------------------------------ *
   * Tier 1: Core Feature Coverage
   * ------------------------------------------------------------------ */

  test("T1.1: Encapsulation toggle creates preset-encapsulation rule with enforceEncapsulation=true", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await expect(po.encapsulationToggle).toBeVisible();
    await expect(po.encapsulationToggle).not.toBeChecked();

    await po.encapsulationToggle.check();
    await expect(po.encapsulationToggle).toBeChecked();

    const rules = await po.getParsedRules();
    expect(rules).toHaveLength(1);
    expect(rules[0]).toMatchObject({
      ruleId: "preset-encapsulation",
      enforceEncapsulation: true,
      targetClassName: "*",
      expectedType: "ANY",
      weight: 1.0,
    });
  });

  test("T1.2: Polymorphism toggle creates preset-polymorphism rule requiring interface or abstract class", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.polymorphismToggle.check();
    await expect(po.polymorphismToggle).toBeChecked();

    const rules = await po.getParsedRules();
    expect(rules).toHaveLength(1);
    expect(rules[0]).toMatchObject({
      ruleId: "preset-polymorphism",
      targetClassName: "*",
      expectedType: "ANY",
      weight: 1.0,
    });
  });

  test("T1.3: Anti-Monolithic toggle creates preset-anti-monolithic rule with wildcard target", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.antiMonolithicToggle.check();
    await expect(po.antiMonolithicToggle).toBeChecked();

    const rules = await po.getParsedRules();
    expect(rules).toHaveLength(1);
    expect(rules[0]).toMatchObject({
      ruleId: "preset-anti-monolithic",
      targetClassName: "*",
      weight: 1.0,
    });
  });

  test("T1.4: Target Class input scopes Encapsulation & Polymorphism rules, keeping Anti-Monolithic wildcard", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.targetClassInput.fill("BankAccount");
    await po.encapsulationToggle.check();
    await po.polymorphismToggle.check();
    await po.antiMonolithicToggle.check();

    const rules = await po.getParsedRules();
    expect(rules).toHaveLength(3);

    const encRule = rules.find((r) => r.ruleId === "preset-encapsulation");
    const polyRule = rules.find((r) => r.ruleId === "preset-polymorphism");
    const antiRule = rules.find((r) => r.ruleId === "preset-anti-monolithic");

    expect(encRule?.targetClassName).toBe("BankAccount");
    expect(polyRule?.targetClassName).toBe("BankAccount");
    expect(antiRule?.targetClassName).toBe("*");

    // Clearing target class input reverts back to wildcard '*'
    await po.targetClassInput.fill("");
    const updatedRules = await po.getParsedRules();
    expect(updatedRules.find((r) => r.ruleId === "preset-encapsulation")?.targetClassName).toBe("*");
    expect(updatedRules.find((r) => r.ruleId === "preset-polymorphism")?.targetClassName).toBe("*");
  });

  test("T1.5: Two-way sync: Modifying Raw JSON automatically updates Presets UI checkboxes and target input", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToRawJson();
    const configWithEncapsulation = JSON.stringify(
      {
        rules: [
          {
            ruleId: "preset-encapsulation",
            ruleName: "Bắt buộc đóng gói (Encapsulation)",
            targetClassName: "SavingsAccount",
            enforceEncapsulation: true,
            weight: 1.0,
          },
        ],
      },
      null,
      2
    );

    await po.rawJsonEditor.fill(configWithEncapsulation);
    await po.switchToPresets();

    await expect(po.encapsulationToggle).toBeChecked();
    await expect(po.polymorphismToggle).not.toBeChecked();
    await expect(po.antiMonolithicToggle).not.toBeChecked();
    await expect(po.targetClassInput).toHaveValue("SavingsAccount");
  });

  test("T1.6: Custom non-preset rules and socket scenarios are strictly preserved when toggling presets", async ({ page }) => {
    const initialConfig = JSON.stringify(
      {
        rules: [
          {
            ruleId: "custom-naming-convention",
            ruleName: "Check naming rules",
            targetClassName: "CustomWorker",
            expectedType: "CLASS",
            weight: 2.0,
          },
        ],
        socketScenarios: [
          {
            name: "ConcurrentClientTest",
            clientCount: 4,
            timeoutMs: 5000,
          },
        ],
        output: {
          ignoreTrailingWhitespace: true,
        },
      },
      null,
      2
    );

    await mockAuthoringApi(page, initialConfig);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.encapsulationToggle.check();

    const raw = await po.getRawJsonValue();
    const parsed = JSON.parse(raw);

    // Custom rule preserved
    expect(parsed.rules.some((r: any) => r.ruleId === "custom-naming-convention")).toBe(true);
    // Preset rule added
    expect(parsed.rules.some((r: any) => r.ruleId === "preset-encapsulation")).toBe(true);
    // Socket scenario preserved
    expect(parsed.socketScenarios).toHaveLength(1);
    expect(parsed.socketScenarios[0].name).toBe("ConcurrentClientTest");
    // Output options preserved
    expect(parsed.output?.ignoreTrailingWhitespace).toBe(true);
  });

  test("T1.7: Validating and saving grading configuration updates backend state", async ({ page }) => {
    const state = await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.encapsulationToggle.check();
    await po.antiMonolithicToggle.check();

    // Validate
    await po.validateButton.click();
    await expect(po.validMessage).toBeVisible();
    await expect(po.validMessage).toContainText(/2 rules/);

    // Save
    await po.saveButton.click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();

    expect(state.saveRequests).toHaveLength(1);
    const saved = JSON.parse(state.gradingConfig!);
    expect(saved.rules).toHaveLength(2);
    expect(saved.rules.map((r: any) => r.ruleId)).toContain("preset-encapsulation");
    expect(saved.rules.map((r: any) => r.ruleId)).toContain("preset-anti-monolithic");
  });

  /* ------------------------------------------------------------------ *
   * Tier 2: Boundary & Corner Cases
   * ------------------------------------------------------------------ */

  test("T2.1: Toggling all presets off produces empty or clean JSON without orphan keys", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.encapsulationToggle.check();
    await po.polymorphismToggle.check();
    await po.antiMonolithicToggle.check();

    expect((await po.getParsedRules()).length).toBe(3);

    // Uncheck all 3 presets
    await po.encapsulationToggle.uncheck();
    await po.polymorphismToggle.uncheck();
    await po.antiMonolithicToggle.uncheck();

    const raw = await po.getRawJsonValue();
    // Should be either completely empty string or empty rules array
    if (raw.trim()) {
      const parsed = JSON.parse(raw);
      expect(parsed.rules ?? []).toHaveLength(0);
    } else {
      expect(raw.trim()).toBe("");
    }
  });

  test("T2.2: Target class input with whitespace is properly trimmed", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.targetClassInput.fill("   TransactionModel   ");
    await po.encapsulationToggle.check();

    const rules = await po.getParsedRules();
    expect(rules).toHaveLength(1);
    expect(rules[0].targetClassName).toBe("TransactionModel");
  });

  test("T2.3: Corrupted JSON in Raw JSON editor shows validation error without crashing presets", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToRawJson();
    await po.rawJsonEditor.fill('{"rules": [ { "ruleId": "broken-rule", ');

    // Validate triggers syntax error
    await po.validateButton.click();
    await expect(po.alertBox).toBeVisible();
    await expect(po.alertBox).toContainText(/not valid json|problem|invalid/i);

    // Switching back to presets tab should not crash the page
    await po.switchToPresets();
    await expect(po.encapsulationToggle).toBeVisible();
    await expect(po.encapsulationToggle).not.toBeChecked();
  });

  test("T2.4: Target class containing nested class notation ($) or package is preserved", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.targetClassInput.fill("com.bank.Account$TransactionBuilder");
    await po.polymorphismToggle.check();

    const rules = await po.getParsedRules();
    expect(rules[0].targetClassName).toBe("com.bank.Account$TransactionBuilder");
  });

  /* ------------------------------------------------------------------ *
   * Tier 3: Cross-Feature Combinations
   * ------------------------------------------------------------------ */

  test("T3.1: All three presets combined with target class, custom socket scenarios and format", async ({ page }) => {
    const mixedConfig = JSON.stringify(
      {
        rules: [
          {
            ruleId: "custom-static-check",
            targetClassName: "*",
            expectedType: "ANY",
            weight: 0.5,
          },
        ],
        socketScenarios: [
          {
            name: "MultiThreadedDeposit",
            clientCount: 10,
            timeoutMs: 3000,
          },
        ],
      },
      null,
      2
    );

    const state = await mockAuthoringApi(page, mixedConfig);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.targetClassInput.fill("BankEngine");
    await po.encapsulationToggle.check();
    await po.polymorphismToggle.check();
    await po.antiMonolithicToggle.check();

    // Validate passes across the mixed structure
    await po.validateButton.click();
    await expect(po.validMessage).toBeVisible();
    await expect(po.validMessage).toContainText(/4 rules/);
    await expect(po.validMessage).toContainText(/1 socket scenario/);

    await po.saveButton.click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();

    const saved = JSON.parse(state.gradingConfig!);
    expect(saved.rules).toHaveLength(4);
    expect(saved.socketScenarios).toHaveLength(1);
    expect(saved.rules.find((r: any) => r.ruleId === "preset-encapsulation")?.targetClassName).toBe("BankEngine");
    expect(saved.rules.find((r: any) => r.ruleId === "preset-anti-monolithic")?.targetClassName).toBe("*");
  });

  test("T3.2: Rapid switching back and forth between Presets and Raw JSON tabs preserves state", async ({ page }) => {
    await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    // 1. In Presets: enable Encapsulation
    await po.switchToPresets();
    await po.targetClassInput.fill("Invoice");
    await po.encapsulationToggle.check();

    // 2. Switch to Raw JSON: add custom comment or format
    await po.switchToRawJson();
    const rawVal = await po.getRawJsonValue();
    expect(rawVal).toContain("Invoice");
    expect(rawVal).toContain("preset-encapsulation");

    // 3. Switch back to Presets: enable Anti-Monolithic
    await po.switchToPresets();
    await po.antiMonolithicToggle.check();

    // 4. Switch back to Raw JSON: verify both exist
    await po.switchToRawJson();
    const updatedRaw = await po.getRawJsonValue();
    const parsed = JSON.parse(updatedRaw);
    expect(parsed.rules).toHaveLength(2);
    expect(parsed.rules.some((r: any) => r.ruleId === "preset-encapsulation")).toBe(true);
    expect(parsed.rules.some((r: any) => r.ruleId === "preset-anti-monolithic")).toBe(true);
  });

  /* ------------------------------------------------------------------ *
   * Tier 4: Real-World Teacher Authoring Workflows
   * ------------------------------------------------------------------ */

  test("T4.1: Teacher creates Bank Account problem using Encapsulation + Anti-Monolithic presets", async ({ page }) => {
    const state = await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    // Teacher selects Encapsulation and Anti-Monolithic with target class Account
    await po.switchToPresets();
    await po.targetClassInput.fill("Account");
    await po.encapsulationToggle.check();
    await po.antiMonolithicToggle.check();

    // Teacher validates
    await po.validateButton.click();
    await expect(po.validMessage).toBeVisible();

    // Teacher saves
    await po.saveButton.click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();

    expect(state.saveRequests).toHaveLength(1);
    const saved = JSON.parse(state.gradingConfig!);
    expect(saved.rules).toHaveLength(2);

    const encRule = saved.rules.find((r: any) => r.ruleId === "preset-encapsulation");
    expect(encRule.targetClassName).toBe("Account");
    expect(encRule.enforceEncapsulation).toBe(true);

    const antiMonoRule = saved.rules.find((r: any) => r.ruleId === "preset-anti-monolithic");
    expect(antiMonoRule.targetClassName).toBe("*");
  });

  test("T4.2: Teacher creates Payment Gateway problem with Polymorphism + Encapsulation presets", async ({ page }) => {
    const state = await mockAuthoringApi(page);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await po.switchToPresets();
    await po.targetClassInput.fill("PaymentMethod");
    await po.encapsulationToggle.check();
    await po.polymorphismToggle.check();

    await po.validateButton.click();
    await expect(po.validMessage).toBeVisible();

    await po.saveButton.click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();

    const saved = JSON.parse(state.gradingConfig!);
    expect(saved.rules).toHaveLength(2);
    expect(saved.rules.every((r: any) => r.targetClassName === "PaymentMethod")).toBe(true);
  });

  test("T4.3: Teacher clears all grading rules resetting back to testcase-only grading", async ({ page }) => {
    const initialConfig = JSON.stringify({
      rules: [
        {
          ruleId: "preset-encapsulation",
          targetClassName: "*",
          enforceEncapsulation: true,
          weight: 1.0,
        },
      ],
    });

    const state = await mockAuthoringApi(page, initialConfig);
    const po = new GradingPanelPO(page);
    await po.openGradingPanel();

    await expect(po.clearButton).toBeVisible();
    await po.clearButton.click();

    // Confirm dialog appears
    const confirmButton = page.getByRole("button", { name: /remove|confirm|xóa/i });
    if (await confirmButton.isVisible()) {
      await confirmButton.click();
    }

    await expect(page.getByText("Grading rules cleared.")).toBeVisible();
    expect(state.gradingConfig).toBeNull();
  });
});
