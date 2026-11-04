import { test, expect } from "@playwright/test";
import { formatCode } from "../src/lib/format-code";

// Kiểm tra hàm thuần, không cần trình duyệt.
test.describe("formatCode", () => {
  test("re-indents Java by braces and ignores braces in strings and comments", () => {
    const messy = [
      "public class Solution {",
      "public static void main(String[] args) {\t",
      "  String s = \"{ not a block\";  ",
      "        // } not a block either",
      "if (s.isEmpty()) {",
      "System.out.println('}');",
      "} else {",
      "foo(1,",
      "2);",
      "}",
      "/*",
      "* note {",
      "*/",
      "}",
      "}",
      "",
      "",
      "",
    ].join("\r\n");
    expect(formatCode(messy, "JAVA")).toBe(
      [
        "public class Solution {",
        "    public static void main(String[] args) {",
        "        String s = \"{ not a block\";",
        "        // } not a block either",
        "        if (s.isEmpty()) {",
        "            System.out.println('}');",
        "        } else {",
        "            foo(1,",
        "                2);",
        "        }",
        "        /*",
        "         * note {",
        "         */",
        "    }",
        "}",
        "",
      ].join("\n")
    );
  });

  test("skips escaped quotes and block comments that close on the same line", () => {
    const code = ['class A {', 'String t = "a\\"{";', "char c = '\\'';", "/* { */ int x = 1;", "}"].join("\n");
    expect(formatCode(code, "JAVA")).toBe(
      ['class A {', '    String t = "a\\"{";', "    char c = '\\'';", "    /* { */ int x = 1;", "}", ""].join("\n")
    );
  });

  test("keeps Python indentation, only expands tabs and trims trailing spaces", () => {
    expect(formatCode("def f():\n\treturn 1   \n", "PYTHON")).toBe("def f():\n    return 1\n");
  });
});
