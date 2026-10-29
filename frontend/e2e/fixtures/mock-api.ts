import { type Page } from "@playwright/test";
import type {
  ContestInfo,
  Difficulty,
  Language,
  ProblemFull,
  ProblemSummary,
  ProctoringResult,
  Submission,
  TestCase,
} from "../../src/types/api";

export interface MockUser {
  id: number;
  username: string;
  email: string;
  password: string;
  role: "USER" | "INSTRUCTOR" | "ADMIN";
  status: "ACTIVE";
}

export interface MockApiOptions {
  existingUsers?: MockUser[];
  customProblems?: ProblemFull[];
  customContests?: ContestInfo[];
}

export interface MockApiController {
  users: Map<string, MockUser>;
  proctoringViolations: Map<string, { warnings: number; blurs: number; pastes: number; lastBlurAt: number; disqualified: boolean }>;
  setViolations: (contestId: number, count: number) => void;
  disqualifyUser: (contestId: number) => void;
  addUser: (user: MockUser) => void;
  addProblem: (problem: ProblemFull) => void;
}

/**
 * Generate a unique random test user account.
 */
export function generateUniqueUser(prefix = "user") {
  const ts = Date.now();
  const rand = Math.random().toString(36).substring(2, 7);
  return {
    username: `${prefix}_${ts}_${rand}`,
    email: `${prefix}_${ts}_${rand}@example.com`,
    password: "Password123!",
  };
}

function b64url(o: object) {
  const str = JSON.stringify(o);
  return Buffer.from(str, "utf8").toString("base64url");
}

/**
 * Create a valid 3-part base64url encoded JWT mock token for testing.
 */
export function createMockJwt(user: { id: number; username: string; role?: string; email?: string }) {
  const header = b64url({ alg: "HS256", typ: "JWT" });
  const payload = b64url({
    userId: user.id,
    sub: user.username,
    username: user.username,
    role: user.role || "USER",
    email: user.email,
    exp: Math.floor(Date.now() / 1000) + 86400,
  });
  return `${header}.${payload}.mock_sig`;
}

/**
 * Sets authenticated JWT tokens in browser localStorage for direct authenticated testing.
 */
export async function setMockAuthSession(page: Page, user?: Partial<MockUser>) {
  const targetUser = {
    id: user?.id ?? 1,
    username: user?.username ?? "student",
    role: user?.role ?? "USER",
    email: user?.email ?? "student@example.com",
  };
  const token = createMockJwt(targetUser);
  await page.addInitScript(
    ({ tok, refresh }) => {
      try {
        localStorage.setItem("access_token", tok);
        localStorage.setItem("refresh_token", refresh);
      } catch {}
    },
    { tok: token, refresh: "mock_refresh_token" }
  );
}

/**
 * Generate a unique random problem.
 */
export function generateUniqueProblem(id = 9999): ProblemFull {
  const ts = Date.now();
  const rand = Math.random().toString(36).substring(2, 6);
  return {
    id,
    slug: `problem-${id}-${ts}-${rand}`,
    title: `Dynamic Problem ${id} ${rand}`,
    difficulty: "MEDIUM" as Difficulty,
    tags: ["Algorithms", "Testing"],
    description: `## Problem Description\n\nThis is dynamic problem ${id} generated for automated testing.`,
    timeLimitMs: 1000,
    memoryLimitMb: 256,
    status: "PUBLISHED",
    likes: 10,
    dislikes: 1,
  };
}

/**
 * Helper to mock Fullscreen API in headless Chromium.
 */
export async function installFullscreenMock(page: Page) {
  await page.addInitScript(() => {
    let _fsElement: Element | null = null;

    Object.defineProperty(document, "fullscreenEnabled", {
      configurable: true,
      get: () => true,
    });

    Object.defineProperty(document, "fullscreenElement", {
      configurable: true,
      get: () => _fsElement,
    });

    Element.prototype.requestFullscreen = function () {
      _fsElement = this;
      document.dispatchEvent(new Event("fullscreenchange"));
      return Promise.resolve();
    };

    document.exitFullscreen = function () {
      _fsElement = null;
      document.dispatchEvent(new Event("fullscreenchange"));
      return Promise.resolve();
    };

    // Expose helpers on window for tests to manipulate fullscreen status
    (window as any).__setFullscreen = (active: boolean) => {
      _fsElement = active ? document.documentElement : null;
      document.dispatchEvent(new Event("fullscreenchange"));
    };
  });
}

/**
 * Helper to simulate exiting fullscreen in tests.
 */
export async function triggerFullscreenExit(page: Page) {
  await page.evaluate(() => {
    if (typeof (window as any).__setFullscreen === "function") {
      (window as any).__setFullscreen(false);
    } else {
      document.dispatchEvent(new Event("fullscreenchange"));
    }
  });
}

/**
 * Helper to simulate entering fullscreen in tests.
 */
export async function triggerFullscreenEnter(page: Page) {
  await page.evaluate(() => {
    if (typeof (window as any).__setFullscreen === "function") {
      (window as any).__setFullscreen(true);
    } else {
      document.dispatchEvent(new Event("fullscreenchange"));
    }
  });
}

/**
 * Set up network route interception for all Mini LeetCode /api/v1/* endpoints.
 */
export async function setupMockApi(page: Page, options: MockApiOptions = {}): Promise<MockApiController> {
  const users = new Map<string, MockUser>();
  const proctoringViolations = new Map<string, { warnings: number; blurs: number; pastes: number; lastBlurAt: number; disqualified: boolean }>();

  // Default seeded users
  const defaultUsers: MockUser[] = [
    { id: 1, username: "student", email: "student@example.com", password: "Password123!", role: "USER", status: "ACTIVE" },
    { id: 2, username: "teacher1", email: "teacher1@example.com", password: "Password123!", role: "INSTRUCTOR", status: "ACTIVE" },
    { id: 3, username: "admin", email: "admin@example.com", password: "Password123!", role: "ADMIN", status: "ACTIVE" },
    ...(options.existingUsers ?? []),
  ];
  for (const u of defaultUsers) {
    users.set(u.username.toLowerCase(), u);
    users.set(u.email.toLowerCase(), u);
  }

  // Default seeded problems
  const problems: ProblemFull[] = options.customProblems ?? [
    {
      id: 1,
      slug: "two-sum",
      title: "Two Sum",
      difficulty: "EASY",
      tags: ["Array", "Hash Table"],
      description: "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.",
      timeLimitMs: 1000,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 1540,
      dislikes: 42,
    },
    {
      id: 2,
      slug: "add-two-numbers",
      title: "Add Two Numbers",
      difficulty: "MEDIUM",
      tags: ["Linked List", "Math"],
      description: "You are given two non-empty linked lists representing two non-negative integers. The digits are stored in reverse order.",
      timeLimitMs: 2000,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 980,
      dislikes: 60,
    },
    {
      id: 3,
      slug: "longest-substring-without-repeating-characters",
      title: "Longest Substring Without Repeating Characters",
      difficulty: "MEDIUM",
      tags: ["Hash Table", "String", "Sliding Window"],
      description: "Given a string `s`, find the length of the longest substring without duplicate characters.",
      timeLimitMs: 1500,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 1250,
      dislikes: 35,
    },
    {
      id: 4,
      slug: "median-of-two-sorted-arrays",
      title: "Median of Two Sorted Arrays",
      difficulty: "HARD",
      tags: ["Array", "Binary Search", "Divide and Conquer"],
      description: "Given two sorted arrays `nums1` and `nums2` of size `m` and `n` respectively, return the median of the two sorted arrays.",
      timeLimitMs: 2500,
      memoryLimitMb: 512,
      status: "PUBLISHED",
      likes: 850,
      dislikes: 40,
    },
    {
      id: 5,
      slug: "valid-parentheses",
      title: "Valid Parentheses",
      difficulty: "EASY",
      tags: ["String", "Stack"],
      description: "Given a string `s` containing just the characters `'('`, `')'`, `'{'`, `'}'`, `'['` and `']'`, determine if the input string is valid.",
      timeLimitMs: 1000,
      memoryLimitMb: 128,
      status: "PUBLISHED",
      likes: 2100,
      dislikes: 15,
    },
    {
      id: 6,
      slug: "merge-k-sorted-lists",
      title: "Merge k Sorted Lists",
      difficulty: "HARD",
      tags: ["Linked List", "Divide and Conquer", "Heap"],
      description: "You are given an array of `k` linked-lists lists, each linked-list is sorted in ascending order. Merge all the linked-lists into one sorted linked-list.",
      timeLimitMs: 3000,
      memoryLimitMb: 512,
      status: "PUBLISHED",
      likes: 920,
      dislikes: 55,
    },
    {
      id: 7,
      slug: "climbing-stairs",
      title: "Climbing Stairs",
      difficulty: "EASY",
      tags: ["Math", "Dynamic Programming"],
      description: "You are climbing a staircase. It takes `n` steps to reach the top. Each time you can either climb 1 or 2 steps.",
      timeLimitMs: 1000,
      memoryLimitMb: 128,
      status: "PUBLISHED",
      likes: 1400,
      dislikes: 20,
    },
    {
      id: 8,
      slug: "coin-change",
      title: "Coin Change",
      difficulty: "MEDIUM",
      tags: ["Array", "Dynamic Programming", "Breadth-First Search"],
      description: "You are given an integer array `coins` representing coins of different denominations and an integer `amount`.",
      timeLimitMs: 2000,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 1100,
      dislikes: 45,
    },
    {
      id: 9,
      slug: "binary-search",
      title: "Binary Search",
      difficulty: "EASY",
      tags: ["Array", "Binary Search"],
      description: "Given an array of integers `nums` which is sorted in ascending order, and an integer `target`, write a function to search `target` in `nums`.",
      timeLimitMs: 1000,
      memoryLimitMb: 128,
      status: "PUBLISHED",
      likes: 1650,
      dislikes: 25,
    },
    {
      id: 10,
      slug: "lru-cache",
      title: "LRU Cache",
      difficulty: "MEDIUM",
      tags: ["Hash Table", "Linked List", "Design"],
      description: "Design a data structure that follows the constraints of a Least Recently Used (LRU) cache.",
      timeLimitMs: 2000,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 2200,
      dislikes: 80,
    },
    {
      id: 11,
      slug: "word-ladder",
      title: "Word Ladder",
      difficulty: "HARD",
      tags: ["Hash Table", "String", "Breadth-First Search"],
      description: "A transformation sequence from word `beginWord` to word `endWord` using a dictionary `wordList` is a sequence of words.",
      timeLimitMs: 3000,
      memoryLimitMb: 512,
      status: "PUBLISHED",
      likes: 1300,
      dislikes: 90,
    },
    {
      id: 12,
      slug: "maximum-subarray",
      title: "Maximum Subarray",
      difficulty: "MEDIUM",
      tags: ["Array", "Divide and Conquer", "Dynamic Programming"],
      description: "Given an integer array `nums`, find the subarray with the largest sum, and return its sum.",
      timeLimitMs: 1500,
      memoryLimitMb: 256,
      status: "PUBLISHED",
      likes: 1800,
      dislikes: 50,
    },
    {
      id: 13,
      slug: "invert-binary-tree",
      title: "Invert Binary Tree",
      difficulty: "EASY",
      tags: ["Tree", "Binary Tree", "Breadth-First Search"],
      description: "Given the `root` of a binary tree, invert the tree, and return its root.",
      timeLimitMs: 1000,
      memoryLimitMb: 128,
      status: "PUBLISHED",
      likes: 1950,
      dislikes: 15,
    },
    {
      id: 14,
      slug: "trapping-rain-water",
      title: "Trapping Rain Water",
      difficulty: "HARD",
      tags: ["Array", "Two Pointers", "Dynamic Programming", "Stack"],
      description: "Given `n` non-negative integers representing an elevation map where the width of each bar is 1, compute how much water it can trap after raining.",
      timeLimitMs: 2500,
      memoryLimitMb: 512,
      status: "PUBLISHED",
      likes: 2600,
      dislikes: 110,
    },
  ];

  // Default test cases map
  const testCasesMap: Record<number, TestCase[]> = {
    1: [
      { id: 101, orderIndex: 1, input: "nums = [2,7,11,15], target = 9", expectedOutput: "[0,1]", isHidden: false, explanation: "nums[0] + nums[1] == 9" },
      { id: 102, orderIndex: 2, input: "nums = [3,2,4], target = 6", expectedOutput: "[1,2]", isHidden: false },
      { id: 103, orderIndex: 3, input: "nums = [3,3], target = 6", expectedOutput: "[0,1]", isHidden: true },
    ],
    2: [
      { id: 201, orderIndex: 1, input: "l1 = [2,4,3], l2 = [5,6,4]", expectedOutput: "[7,0,8]", isHidden: false },
    ],
  };

  // Default seeded contests
  const contests: ContestInfo[] = options.customContests ?? [
    {
      id: 101,
      title: "Mini LeetCode Weekly Contest 101",
      startTime: new Date(Date.now() - 3600000).toISOString(),
      endTime: new Date(Date.now() + 7200000).toISOString(),
      status: "RUNNING",
      scoringPolicy: "ACM_ICPC",
      maxViolationsAllowed: 3,
      myStatus: "REGISTERED",
      myViolations: 0,
      problems: [
        { id: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY", label: "A" },
        { id: 2, slug: "add-two-numbers", title: "Add Two Numbers", difficulty: "MEDIUM", label: "B" },
        { id: 3, slug: "longest-substring-without-repeating-characters", title: "Longest Substring Without Repeating Characters", difficulty: "MEDIUM", label: "C" },
      ],
    },
    {
      id: 102,
      title: "Mini LeetCode Biweekly Contest 42",
      startTime: new Date(Date.now() + 86400000).toISOString(),
      endTime: new Date(Date.now() + 93600000).toISOString(),
      status: "SCHEDULED",
      scoringPolicy: "ACM_ICPC",
      maxViolationsAllowed: 3,
      problems: [
        { id: 5, slug: "valid-parentheses", title: "Valid Parentheses", difficulty: "EASY", label: "A" },
      ],
    },
  ];

  let nextSubmissionId = 1000;
  const submissions = new Map<number, Submission>();
  const drafts = new Map<string, { value: string; savedAt: number }>();

  // Main Route Handler
  await page.route("**/api/v1/**", async (route, request) => {
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    const json = (data: unknown, status = 200, contentType = "application/json") =>
      route.fulfill({
        status,
        contentType,
        body: JSON.stringify(data),
      });

    const problemDetail = (status: number, title: string, detail: string, typeSuffix: string, extra?: Record<string, any>) =>
      json(
        {
          type: `https://api.mini-leetcode.com/errors/${typeSuffix}`,
          title,
          status,
          detail,
          instance: path,
          timestamp: new Date().toISOString(),
          ...extra,
        },
        status,
        "application/problem+json"
      );

    // ----------------------------------------------------
    // 1. AUTH ENDPOINTS
    // ----------------------------------------------------

    // POST /api/v1/auth/register
    if (path === "/api/v1/auth/register" && method === "POST") {
      let body: any = {};
      try {
        body = JSON.parse(request.postData() || "{}");
      } catch {
        return problemDetail(400, "Bad Request", "Malformed JSON payload", "bad-request");
      }

      const { username, email, password } = body;
      const invalidParams: Record<string, string> = {};

      if (username === "trigger_400_validation") {
        return problemDetail(400, "Bad Request", "Validation failed for request fields", "validation-failed", {
          invalid_params: {
            username: "Tên đăng nhập không hợp lệ theo RFC 7807",
            email: "Email thuộc tên miền bị chặn",
          },
        });
      }

      if (!username || typeof username !== "string" || username.trim().length < 3 || username.length > 50) {
        invalidParams.username = "Username must be between 3 and 50 characters";
      }
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!email || typeof email !== "string" || !emailRegex.test(email.trim())) {
        invalidParams.email = "Email must be valid";
      }
      if (!password || typeof password !== "string" || password.length < 8 || password.length > 100) {
        invalidParams.password = "Password must be between 8 and 100 characters";
      }

      if (Object.keys(invalidParams).length > 0) {
        return problemDetail(400, "Bad Request", "Validation failed for request fields", "validation-failed", {
          invalid_params: invalidParams,
        });
      }

      const lowerUsername = username.trim().toLowerCase();
      const lowerEmail = email.trim().toLowerCase();

      if (users.has(lowerUsername)) {
        return problemDetail(409, "Resource Conflict", `Username '${username.trim()}' is already taken`, "conflict");
      }
      if (users.has(lowerEmail)) {
        return problemDetail(409, "Resource Conflict", `Email '${email.trim()}' is already taken`, "conflict");
      }

      const newUser: MockUser = {
        id: users.size + 1,
        username: username.trim(),
        email: email.trim(),
        password,
        role: "USER",
        status: "ACTIVE",
      };
      users.set(lowerUsername, newUser);
      users.set(lowerEmail, newUser);

      const accessToken = createMockJwt(newUser);
      const refreshToken = `mock_refresh_token_${newUser.id}_${Date.now()}`;

      const responsePayload = {
        accessToken,
        refreshToken,
        tokenType: "Bearer",
        expiresIn: 86400,
        user: {
          id: newUser.id,
          username: newUser.username,
          email: newUser.email,
          role: newUser.role,
          status: newUser.status,
        },
      };

      return json({ success: true, statusCode: 201, message: "User registered successfully", data: responsePayload, ...responsePayload }, 201);
    }

    // POST /api/v1/auth/login
    if (path === "/api/v1/auth/login" && method === "POST") {
      let body: any = {};
      try {
        body = JSON.parse(request.postData() || "{}");
      } catch {
        return problemDetail(400, "Bad Request", "Malformed JSON payload", "bad-request");
      }

      const identifier = (body.usernameOrEmail || body.username || "").trim().toLowerCase();
      const password = body.password || "";

      const user = users.get(identifier);
      if (!user || user.password !== password) {
        return problemDetail(401, "Authentication Failed", "Invalid username or password", "unauthorized");
      }

      const accessToken = createMockJwt(user);
      const refreshToken = `mock_refresh_token_${user.id}_${Date.now()}`;

      const responsePayload = {
        accessToken,
        refreshToken,
        tokenType: "Bearer",
        expiresIn: 86400,
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
          role: user.role,
          status: user.status,
        },
      };

      return json({ success: true, statusCode: 200, message: "Login successful", data: responsePayload, ...responsePayload });
    }

    // POST /api/v1/auth/refresh
    if (path === "/api/v1/auth/refresh" && method === "POST") {
      const accessToken = createMockJwt({ id: 1, username: "student", role: "USER" });
      const refreshToken = `mock_refresh_token_refreshed_${Date.now()}`;
      const responsePayload = { accessToken, refreshToken, tokenType: "Bearer", expiresIn: 86400 };
      return json({ success: true, statusCode: 200, data: responsePayload, ...responsePayload });
    }

    // POST /api/v1/auth/logout
    if (path === "/api/v1/auth/logout" && method === "POST") {
      return json({ success: true, statusCode: 200, message: "Logged out successfully" });
    }

    // GET /api/v1/auth/me
    if (path === "/api/v1/auth/me" && method === "GET") {
      const student = users.get("student")!;
      return json({
        success: true,
        data: { id: student.id, username: student.username, email: student.email, role: student.role, status: student.status },
      });
    }

    // ----------------------------------------------------
    // 2. PROBLEMS ENDPOINTS
    // ----------------------------------------------------

    // GET /api/v1/problems
    if (path === "/api/v1/problems" && method === "GET") {
      const pageIndex = parseInt(url.searchParams.get("page") || "0", 10);
      const pageSize = parseInt(url.searchParams.get("size") || "8", 10);
      const searchQuery = (url.searchParams.get("search") || "").trim().toLowerCase();
      const difficulty = (url.searchParams.get("difficulty") || "").toUpperCase();

      let filtered = [...problems];
      if (searchQuery) {
        filtered = filtered.filter(
          (p) =>
            p.title.toLowerCase().includes(searchQuery) ||
            p.slug.toLowerCase().includes(searchQuery) ||
            p.description.toLowerCase().includes(searchQuery) ||
            p.tags.some((t) => t.toLowerCase().includes(searchQuery))
        );
      }
      if (difficulty) {
        filtered = filtered.filter((p) => p.difficulty === difficulty);
      }

      const totalElements = filtered.length;
      const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
      const start = pageIndex * pageSize;
      const content = filtered.slice(start, start + pageSize).map((p) => ({
        id: p.id,
        slug: p.slug,
        title: p.title,
        difficulty: p.difficulty,
        tags: p.tags,
        progress: p.progress || "NEVER",
      }));

      const pagePayload = {
        content,
        totalElements,
        totalPages,
        number: pageIndex,
        size: pageSize,
        first: pageIndex === 0,
        last: pageIndex >= totalPages - 1,
      };

      return json({ success: true, statusCode: 200, data: pagePayload, ...pagePayload });
    }

    // GET /api/v1/problems/{id}/test-cases
    const testCasesMatch = path.match(/^\/api\/v1\/problems\/(\d+)\/test-cases$/);
    if (testCasesMatch && method === "GET") {
      const pId = parseInt(testCasesMatch[1], 10);
      const cases = testCasesMap[pId] || [
        { id: pId * 100 + 1, orderIndex: 1, input: "Sample input 1", expectedOutput: "Sample output 1", isHidden: false },
        { id: pId * 100 + 2, orderIndex: 2, input: "Sample input 2", expectedOutput: "Sample output 2", isHidden: false },
      ];
      return json({ success: true, statusCode: 200, data: cases, content: cases });
    }

    // POST /api/v1/problems/{id}/run
    const runMatch = path.match(/^\/api\/v1\/problems\/(\d+)\/run$/);
    if (runMatch && method === "POST") {
      const pId = parseInt(runMatch[1], 10);
      let body: any = {};
      try {
        body = JSON.parse(request.postData() || "{}");
      } catch {}

      const runId = ++nextSubmissionId;
      const result: Submission = {
        id: runId,
        problemId: pId,
        language: (body.language as Language) || "JAVA",
        status: "COMPLETED",
        verdict: "AC",
        executionTimeMs: 42,
        memoryUsedKb: 18400,
        passTestCases: 2,
        totalTestCases: 2,
        errorMessage: null,
        createdAt: new Date().toISOString(),
      };
      submissions.set(runId, result);
      return json({ success: true, statusCode: 200, data: result, ...result });
    }

    // POST /api/v1/problems/{id}/submit
    const submitMatch = path.match(/^\/api\/v1\/problems\/(\d+)\/submit$/);
    if (submitMatch && method === "POST") {
      const pId = parseInt(submitMatch[1], 10);
      let body: any = {};
      try {
        body = JSON.parse(request.postData() || "{}");
      } catch {}

      const subId = ++nextSubmissionId;
      const sub: Submission = {
        id: subId,
        problemId: pId,
        contestId: body.contestId ?? null,
        language: (body.language as Language) || "JAVA",
        status: "COMPLETED",
        verdict: "AC",
        executionTimeMs: 55,
        memoryUsedKb: 20480,
        passTestCases: 3,
        totalTestCases: 3,
        errorMessage: null,
        createdAt: new Date().toISOString(),
      };
      submissions.set(subId, sub);
      return json({ success: true, statusCode: 200, data: sub, ...sub });
    }

    // GET /api/v1/problems/{idOrSlug}
    const singleProblemMatch = path.match(/^\/api\/v1\/problems\/([^/]+)$/);
    if (singleProblemMatch && method === "GET") {
      const idOrSlug = singleProblemMatch[1];
      const prob = problems.find((p) => String(p.id) === idOrSlug || p.slug === idOrSlug);
      if (!prob) {
        return problemDetail(404, "Not Found", `Problem '${idOrSlug}' not found`, "not-found");
      }
      return json({ success: true, statusCode: 200, data: prob, ...prob });
    }

    // ----------------------------------------------------
    // 3. SUBMISSIONS ENDPOINTS
    // ----------------------------------------------------

    // GET /api/v1/submissions/{id}
    const singleSubMatch = path.match(/^\/api\/v1\/submissions\/(\d+)$/);
    if (singleSubMatch && method === "GET") {
      const subId = parseInt(singleSubMatch[1], 10);
      const sub = submissions.get(subId) || {
        id: subId,
        problemId: 1,
        language: "JAVA" as Language,
        status: "COMPLETED" as const,
        verdict: "AC" as const,
        executionTimeMs: 38,
        memoryUsedKb: 19200,
        passTestCases: 3,
        totalTestCases: 3,
        errorMessage: null,
        createdAt: new Date().toISOString(),
      };
      return json({ success: true, statusCode: 200, data: sub, ...sub });
    }

    // GET /api/v1/submissions/{id}/results
    const subResultsMatch = path.match(/^\/api\/v1\/submissions\/(\d+)\/results$/);
    if (subResultsMatch && method === "GET") {
      const results = [
        { index: 1, verdict: "AC", hidden: false, input: "nums = [2,7,11,15], target = 9", expected: "[0,1]", actual: "[0,1]", timeMs: 12 },
        { index: 2, verdict: "AC", hidden: false, input: "nums = [3,2,4], target = 6", expected: "[1,2]", actual: "[1,2]", timeMs: 14 },
        { index: 3, verdict: "AC", hidden: true, timeMs: 18 },
      ];
      return json({ success: true, statusCode: 200, data: results });
    }

    // GET /api/v1/submissions/{id}/code
    const subCodeMatch = path.match(/^\/api\/v1\/submissions\/(\d+)\/code$/);
    if (subCodeMatch && method === "GET") {
      return json({ success: true, data: "// Solution submitted code" });
    }

    // GET /api/v1/submissions/my
    if (path === "/api/v1/submissions/my" && method === "GET") {
      const list = Array.from(submissions.values());
      const pagePayload = {
        content: list,
        totalElements: list.length,
        totalPages: 1,
        number: 0,
        size: 15,
      };
      return json({ success: true, data: pagePayload, ...pagePayload });
    }

    // ----------------------------------------------------
    // 4. CONTESTS & PROCTORING ENDPOINTS
    // ----------------------------------------------------

    // GET /api/v1/contests
    if (path === "/api/v1/contests" && method === "GET") {
      return json({ success: true, statusCode: 200, data: contests, content: contests });
    }

    // POST /api/v1/contests/{id}/register
    const contestRegMatch = path.match(/^\/api\/v1\/contests\/(\d+)\/register$/);
    if (contestRegMatch && method === "POST") {
      const cId = parseInt(contestRegMatch[1], 10);
      const contest = contests.find((c) => c.id === cId);
      if (contest) {
        contest.myStatus = "REGISTERED";
      }
      return json({ success: true, statusCode: 200, message: "Registered successfully", contestId: cId });
    }

    // POST /api/v1/contests/{id}/proctoring/events
    const proctorEventMatch = path.match(/^\/api\/v1\/contests\/(\d+)\/proctoring\/events$/);
    if (proctorEventMatch && method === "POST") {
      const cId = parseInt(proctorEventMatch[1], 10);
      let body: any = {};
      try {
        body = JSON.parse(request.postData() || "{}");
      } catch {}

      const key = `${cId}:user`;
      const state = proctoringViolations.get(key) || { warnings: 0, blurs: 0, pastes: 0, lastBlurAt: 0, disqualified: false };

      const eventType = body.eventType;
      const clientTs = body.clientTimestamp || Date.now();
      let awayDurationMs: number | null = null;

      if (eventType === "CLIPBOARD_PASTE") {
        state.pastes += 1;
        state.warnings += 1;
      } else if (eventType === "WINDOW_BLUR") {
        state.lastBlurAt = clientTs;
      } else if (eventType === "WINDOW_FOCUS") {
        if (state.lastBlurAt > 0) {
          awayDurationMs = Math.max(0, clientTs - state.lastBlurAt);
          state.lastBlurAt = 0;
          if (awayDurationMs >= 1500) {
            state.blurs += 1;
            state.warnings += 1;
          }
        }
      }

      if (state.warnings >= 3) {
        state.disqualified = true;
      }
      proctoringViolations.set(key, state);

      const proctorResult: ProctoringResult = {
        contestId: cId,
        userId: 1,
        status: state.disqualified ? "DISQUALIFIED" : "IN_PROGRESS",
        violationCount: state.warnings,
        maxViolationsAllowed: 3,
        isDisqualified: state.disqualified,
        awayDurationMs,
        message: state.disqualified
          ? "Bạn đã bị loại khỏi kỳ thi do vi phạm quy chế quá 3 lần."
          : `Cảnh báo vi phạm quy chế thi (${state.warnings}/3).`,
      };

      return json({ success: true, statusCode: 200, data: proctorResult, ...proctorResult });
    }

    // GET /api/v1/contests/{id}/leaderboard
    const leaderboardMatch = path.match(/^\/api\/v1\/contests\/(\d+)\/leaderboard$/);
    if (leaderboardMatch && method === "GET") {
      const cId = parseInt(leaderboardMatch[1], 10);
      const key = `${cId}:user`;
      const state = proctoringViolations.get(key);
      const isDisq = state?.disqualified ?? false;

      const rows = [
        {
          rank: 1,
          userId: 901,
          username: "algorithm_master",
          solved: 3,
          penaltyMinutes: 48,
          cells: {
            1: { solved: true, attempts: 1, minute: 12 },
            2: { solved: true, attempts: 1, minute: 20 },
            3: { solved: true, attempts: 2, minute: 16 },
          },
        },
        {
          rank: 2,
          userId: 902,
          username: "speed_coder",
          solved: 2,
          penaltyMinutes: 62,
          cells: {
            1: { solved: true, attempts: 1, minute: 15 },
            2: { solved: true, attempts: 3, minute: 47 },
          },
        },
        {
          rank: isDisq ? null : 3,
          userId: 1,
          username: "student",
          solved: isDisq ? 0 : 1,
          penaltyMinutes: 18,
          disqualified: isDisq,
          cells: {
            1: { solved: !isDisq, attempts: 1, minute: 18 },
          },
        },
      ];

      return json({ success: true, statusCode: 200, data: rows, content: rows });
    }

    // GET /api/v1/contests/{id}
    const singleContestMatch = path.match(/^\/api\/v1\/contests\/(\d+)$/);
    if (singleContestMatch && method === "GET") {
      const cId = parseInt(singleContestMatch[1], 10);
      const contest = contests.find((c) => c.id === cId);
      if (!contest) {
        return problemDetail(404, "Not Found", `Contest ${cId} not found`, "not-found");
      }
      const key = `${cId}:user`;
      const state = proctoringViolations.get(key);
      const isDq = state?.disqualified ?? contest.myStatus === "DISQUALIFIED";
      const dynamicContest: ContestInfo = {
        ...contest,
        myStatus: isDq ? "DISQUALIFIED" : contest.myStatus,
        myViolations: state?.warnings ?? contest.myViolations ?? 0,
      };
      return json({ success: true, statusCode: 200, data: dynamicContest, ...dynamicContest });
    }

    // GET & PUT /api/v1/contests/{cid}/problems/{pid}/draft
    const draftMatch = path.match(/^\/api\/v1\/contests\/(\d+)\/problems\/([^/]+)\/draft$/);
    if (draftMatch) {
      const draftKey = `${draftMatch[1]}:${draftMatch[2]}`;
      if (method === "GET") {
        const d = drafts.get(draftKey) || { value: "", savedAt: Date.now() };
        return json({ success: true, data: { code: d.value, updatedAt: new Date(d.savedAt).toISOString() } });
      }
      if (method === "PUT") {
        let body: any = {};
        try {
          body = JSON.parse(request.postData() || "{}");
        } catch {}
        drafts.set(draftKey, { value: body.code || "", savedAt: Date.now() });
        return json({ success: true, message: "Draft saved" });
      }
    }

    // Default continue for other unhandled requests
    return route.continue();
  });

  return {
    users,
    proctoringViolations,
    setViolations: (contestId: number, count: number) => {
      const key = `${contestId}:user`;
      const state = proctoringViolations.get(key) || { warnings: 0, blurs: 0, pastes: 0, lastBlurAt: 0, disqualified: false };
      state.warnings = count;
      state.disqualified = count >= 3;
      proctoringViolations.set(key, state);
      const c = contests.find((x) => x.id === contestId);
      if (c) {
        c.myViolations = count;
        if (count >= 3) c.myStatus = "DISQUALIFIED";
      }
    },
    disqualifyUser: (contestId: number) => {
      const key = `${contestId}:user`;
      const state = proctoringViolations.get(key) || { warnings: 3, blurs: 0, pastes: 0, lastBlurAt: 0, disqualified: true };
      state.warnings = Math.max(3, state.warnings);
      state.disqualified = true;
      proctoringViolations.set(key, state);
      const c = contests.find((x) => x.id === contestId);
      if (c) {
        c.myStatus = "DISQUALIFIED";
        c.myViolations = Math.max(3, state.warnings);
      }
    },
    addUser: (user: MockUser) => {
      users.set(user.username.toLowerCase(), user);
      users.set(user.email.toLowerCase(), user);
    },
    addProblem: (problem: ProblemFull) => {
      problems.push(problem);
    },
  };
}
