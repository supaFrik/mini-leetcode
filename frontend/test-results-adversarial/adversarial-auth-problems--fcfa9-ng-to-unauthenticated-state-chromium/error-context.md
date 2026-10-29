# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: adversarial-auth-problems.spec.ts >> Adversarial Stress Testing: Auth and Problems Flows >> 2. Adversarial Login and Security >> handles corrupted/malformed JWT in localStorage by safely degrading to unauthenticated state
- Location: e2e\adversarial-auth-problems.spec.ts:238:9

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:3005/
Call log:
  - navigating to "http://localhost:3005/", waiting until "load"

```

# Test source

```ts
  145 |           status: 500,
  146 |           contentType: "application/problem+json",
  147 |           body: JSON.stringify({
  148 |             type: "https://api.mini-leetcode.com/errors/internal-error",
  149 |             title: "Internal Server Error",
  150 |             status: 500,
  151 |             detail: "Database cluster synchronization timed out",
  152 |             instance: "/api/v1/auth/register",
  153 |           }),
  154 |         });
  155 |       });
  156 | 
  157 |       await page.goto("/register");
  158 |       await page.waitForLoadState("domcontentloaded");
  159 | 
  160 |       await page.getByLabel("Tên đăng nhập").fill("test_user_500");
  161 |       await page.getByLabel("Email").fill("test500@example.com");
  162 |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  163 | 
  164 |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  165 |       await expect(submitBtn).toBeEnabled();
  166 |       await Promise.all([
  167 |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
  168 |         submitBtn.click(),
  169 |       ]);
  170 | 
  171 |       // Error banner must display RFC 7807 detail
  172 |       await expect(page.getByText("Database cluster synchronization timed out")).toBeVisible();
  173 | 
  174 |       // Form button should be re-enabled and form interactive
  175 |       await expect(submitBtn).toBeEnabled();
  176 |     });
  177 |   });
  178 | 
  179 |   // =========================================================================
  180 |   // 2. ADVERSARIAL LOGIN, JWT HANDLING & OPEN-REDIRECT RESILIENCE
  181 |   // =========================================================================
  182 |   test.describe("2. Adversarial Login and Security", () => {
  183 |     test("neutralizes open-redirect attempts via malicious query parameters", async ({ page }) => {
  184 |       const maliciousTargets = [
  185 |         "//evil.com",
  186 |         "https://attacker.com",
  187 |         "javascript:alert(1)",
  188 |         "/login",
  189 |         "/register",
  190 |       ];
  191 | 
  192 |       for (const target of maliciousTargets) {
  193 |         await page.goto(`/login?next=${encodeURIComponent(target)}`);
  194 |         await page.waitForLoadState("domcontentloaded");
  195 | 
  196 |         await page.getByLabel("Tên đăng nhập").fill("student");
  197 |         await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  198 | 
  199 |         const submitBtn = page.getByRole("button", { name: "Đăng nhập" });
  200 |         await expect(submitBtn).toBeEnabled();
  201 |         await Promise.all([
  202 |           page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
  203 |           submitBtn.click(),
  204 |         ]);
  205 | 
  206 |         // Must redirect to safe internal root '/', NOT to evil.com or loop back
  207 |         await page.waitForURL((url) => url.pathname === "/" || !url.pathname.includes("/login"), { timeout: 10000 });
  208 |         const currentUrl = page.url();
  209 |         expect(currentUrl).not.toContain("evil.com");
  210 |         expect(currentUrl).not.toContain("attacker.com");
  211 |         expect(currentUrl).not.toContain("javascript:");
  212 | 
  213 |         // Clear tokens for next iteration
  214 |         await page.evaluate(() => localStorage.clear());
  215 |       }
  216 |     });
  217 | 
  218 |     test("preserves deep internal route with query parameters on legitimate login", async ({ page }) => {
  219 |       const deepUrl = "/problems?page=2&difficulty=EASY";
  220 |       await page.goto(`/login?next=${encodeURIComponent(deepUrl)}`);
  221 |       await page.waitForLoadState("domcontentloaded");
  222 | 
  223 |       await page.getByLabel("Tên đăng nhập").fill("student");
  224 |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  225 | 
  226 |       const submitBtn = page.getByRole("button", { name: "Đăng nhập" });
  227 |       await expect(submitBtn).toBeEnabled();
  228 |       await Promise.all([
  229 |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
  230 |         submitBtn.click(),
  231 |       ]);
  232 | 
  233 |       // Must redirect to the full deep route with all query params
  234 |       await page.waitForURL((url) => url.pathname.includes("/problems") && url.search.includes("page=2"), { timeout: 10000 });
  235 |       expect(page.url()).toContain("difficulty=EASY");
  236 |     });
  237 | 
  238 |     test("handles corrupted/malformed JWT in localStorage by safely degrading to unauthenticated state", async ({ page }) => {
  239 |       // Set corrupted tokens before navigation
  240 |       await page.addInitScript(() => {
  241 |         localStorage.setItem("access_token", "invalid.not-base64-json.mock_sig");
  242 |         localStorage.setItem("refresh_token", "corrupted_refresh");
  243 |       });
  244 | 
> 245 |       await page.goto("/");
      |                  ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:3005/
  246 |       await page.waitForLoadState("domcontentloaded");
  247 | 
  248 |       // App must not crash; decodeUser handles JSON parse failure gracefully
  249 |       // Header reflects unauthenticated Guest state
  250 |       await expect(page.getByText("Guest")).toBeVisible();
  251 |       await expect(page.getByText("#—")).toBeVisible();
  252 |     });
  253 | 
  254 |     test("handles expired JWT and forces re-authentication on 401 API response", async ({ page }) => {
  255 |       // Create an expired JWT token (exp in 1970)
  256 |       const expiredPayload = {
  257 |         userId: 1,
  258 |         username: "student",
  259 |         role: "USER",
  260 |         exp: 1000,
  261 |       };
  262 |       const header = Buffer.from(JSON.stringify({ alg: "HS256" })).toString("base64url");
  263 |       const payload = Buffer.from(JSON.stringify(expiredPayload)).toString("base64url");
  264 |       const expiredToken = `${header}.${payload}.expired_sig`;
  265 | 
  266 |       await page.addInitScript((tok) => {
  267 |         localStorage.setItem("access_token", tok);
  268 |         localStorage.setItem("refresh_token", "mock_refresh_expired");
  269 |       }, expiredToken);
  270 | 
  271 |       // Mock API to return 401 Unauthorized for expired token and 401 for refresh attempt
  272 |       await page.route("**/api/v1/auth/refresh", async (route) => {
  273 |         await route.fulfill({ status: 401, body: JSON.stringify({ message: "Token expired" }) });
  274 |       });
  275 |       await page.route("**/api/v1/problems*", async (route) => {
  276 |         await route.fulfill({ status: 401, body: JSON.stringify({ message: "Expired JWT" }) });
  277 |       });
  278 | 
  279 |       // Navigate to /problems: api request 401 triggers clearTokens() and window.location.href = "/login"
  280 |       await page.goto("/problems", { waitUntil: "commit" });
  281 | 
  282 |       await expect(page).toHaveURL(/.*\/login/, { timeout: 10000 });
  283 |       const clearedToken = await page.evaluate(() => localStorage.getItem("access_token"));
  284 |       expect(clearedToken).toBeNull();
  285 |     });
  286 |   });
  287 | 
  288 |   // =========================================================================
  289 |   // 3. ADVERSARIAL PROBLEMS SEARCH, FILTERING & BOUNDARIES
  290 |   // =========================================================================
  291 |   test.describe("3. Adversarial Problems Exploration", () => {
  292 |     test.beforeEach(async ({ page }) => {
  293 |       await setMockAuthSession(page, { username: "student", role: "USER" });
  294 |     });
  295 | 
  296 |     test("safely escapes special characters and script tags in search query", async ({ page }) => {
  297 |       await page.goto("/problems");
  298 |       await page.waitForLoadState("domcontentloaded");
  299 | 
  300 |       const searchInput = page.getByLabel("Tìm trong danh sách");
  301 |       await expect(searchInput).toBeVisible({ timeout: 10000 });
  302 | 
  303 |       // Adversarial search string with HTML tags, ampersands, and quotes
  304 |       const adversarialQuery = '?query="foo"&<script>alert(1)</script>';
  305 |       await searchInput.fill(adversarialQuery);
  306 |       await searchInput.press("Enter");
  307 | 
  308 |       // Verify URL is properly encoded without breaking
  309 |       await page.waitForURL((url) => url.searchParams.has("q"), { timeout: 5000 });
  310 |       expect(page.url()).toContain("q=");
  311 | 
  312 |       // Filter chip displays text safely without HTML injection
  313 |       await expect(page.getByText("Không có bài nào khớp bộ lọc.")).toBeVisible();
  314 | 
  315 |       // Clear filter returns to full problem list
  316 |       await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
  317 |       await expect(page.getByText("Two Sum")).toBeVisible();
  318 |     });
  319 | 
  320 |     test("handles extreme out-of-bounds page index without crashing", async ({ page }) => {
  321 |       await page.goto("/problems?page=999999");
  322 |       await page.waitForLoadState("domcontentloaded");
  323 | 
  324 |       // Table should render cleanly with empty message instead of crashing
  325 |       await expect(page.getByText("Status", { exact: true })).toBeVisible({ timeout: 10000 });
  326 |       await expect(page.getByText("Không có bài nào khớp bộ lọc.")).toBeVisible();
  327 | 
  328 |       // Clicking reset restores list
  329 |       await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
  330 |       await expect(page.getByText("Two Sum")).toBeVisible();
  331 |     });
  332 | 
  333 |     test("normalizes negative and non-numeric page parameter to Page 1", async ({ page }) => {
  334 |       await page.goto("/problems?page=-42");
  335 |       await page.waitForLoadState("domcontentloaded");
  336 | 
  337 |       // Page is normalized to 1, Two Sum is visible
  338 |       await expect(page.getByText("Two Sum")).toBeVisible({ timeout: 10000 });
  339 |       await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();
  340 | 
  341 |       // Test non-numeric page parameter
  342 |       await page.goto("/problems?page=not_a_number");
  343 |       await page.waitForLoadState("domcontentloaded");
  344 |       await expect(page.getByText("Two Sum")).toBeVisible({ timeout: 10000 });
  345 |       await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();
```