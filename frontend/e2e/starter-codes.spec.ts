import { expect, test } from "@playwright/test";
import { starterCodes, TEMPLATES } from "../src/lib/templates";

// Unit check (không mở trình duyệt): code khởi đầu bài DSA do admin đặt.
test("starterCodes: admin file wins per language, blank/missing/broken falls back", () => {
  const custom = JSON.stringify([
    { path: "Solution.java", content: "class Solution { /* custom */ }" },
    { path: "solution.py", content: "   " },
  ]);
  const got = starterCodes(custom);
  expect(got.JAVA).toBe("class Solution { /* custom */ }");
  expect(got.PYTHON).toBe(TEMPLATES.PYTHON);

  expect(starterCodes(null)).toEqual(TEMPLATES);
  expect(starterCodes("not json")).toEqual(TEMPLATES);
  expect(starterCodes('[{"path":"Main.java","content":"x"}]')).toEqual(TEMPLATES);
});
