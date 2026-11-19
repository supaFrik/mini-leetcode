import type { Language, SubmissionFile } from "@/types/api";

export const LANGUAGES: { value: Language; label: string; dot: string }[] = [
  { value: "JAVA", label: "Java", dot: "bg-cyan-400" },
  { value: "PYTHON", label: "Python", dot: "bg-yellow-400" },
];
export const MONACO_LANG: Record<Language, string> = { JAVA: "java", PYTHON: "python" };
export const FILE_NAME: Record<Language, string> = { JAVA: "Solution.java", PYTHON: "solution.py" };

export const TEMPLATES: Record<Language, string> = {
  JAVA: `import java.util.*;

public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (!sc.hasNextLine()) return;

        // TODO: Read input from stdin, process, and print to stdout
        // Example for reading line 1 (array) and line 2 (target):
        // String[] parts = sc.nextLine().trim().split("\\\\s+");
        // int[] nums = Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        // int target = Integer.parseInt(sc.nextLine().trim());
    }
}
`,
  PYTHON: `import sys

def main():
    lines = sys.stdin.read().splitlines()
    if not lines:
        return

    # TODO: Read input from stdin, process, and print to stdout
    # Example for reading line 1 (array) and line 2 (target):
    # nums = [int(x) for x in lines[0].split()]
    # target = int(lines[1].strip())

if __name__ == '__main__':
    main()
`,
};

export const LANG_PREF_KEY = "pref:lang";

/**
 * Code khởi đầu bài DSA theo ngôn ngữ. Admin đặt qua `starterTemplates`
 * (JSON `[{ path, content }]`, path = FILE_NAME[lang]); thiếu hoặc rỗng thì dùng TEMPLATES.
 */
export function starterCodes(starterTemplates?: string | null): Record<Language, string> {
  const out = { ...TEMPLATES };
  try {
    const files: unknown = JSON.parse(starterTemplates || "[]");
    if (!Array.isArray(files)) return out;
    for (const l of Object.keys(FILE_NAME) as Language[]) {
      const f = files.find((x) => x?.path === FILE_NAME[l]);
      if (typeof f?.content === "string" && f.content.trim()) out[l] = f.content;
    }
  } catch {
    /* JSON hỏng → mặc định */
  }
  return out;
}

/**
 * Parse danh sách file khởi tạo cho bài OOP đa file từ `starterTemplates`.
 */
export function parseStarterFiles(starterTemplates?: string | null): SubmissionFile[] {
  if (starterTemplates) {
    try {
      const parsed = JSON.parse(starterTemplates);
      if (Array.isArray(parsed) && parsed.length > 0) {
        const list = parsed
          .filter((f): f is SubmissionFile => Boolean(f && typeof f.path === "string" && typeof f.content === "string"))
          .map((f) => ({ path: f.path, content: f.content }));
        if (list.length > 0) return list;
      }
    } catch {
      /* JSON hỏng → dùng mặc định */
    }
  }
  return [
    {
      path: "Main.java",
      content: "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        \n    }\n}\n",
    },
  ];
}
