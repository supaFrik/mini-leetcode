import type { Language } from "@/types/api";

export const LANGUAGES: { value: Language; label: string; dot: string }[] = [
  { value: "JAVA", label: "Java", dot: "bg-cyan-400" },
  { value: "PYTHON", label: "Python", dot: "bg-yellow-400" },
];
export const MONACO_LANG: Record<Language, string> = { JAVA: "java", PYTHON: "python" };
export const FILE_NAME: Record<Language, string> = { JAVA: "Solution.java", PYTHON: "solution.py" };

// Judge chạy chương trình đọc stdin / ghi stdout, nên template là chương trình hoàn chỉnh.
export const TEMPLATES: Record<Language, string> = {
  JAVA: `import java.util.*;

public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO: đọc input, in output
    }
}
`,
  PYTHON: `import sys

def main():
    data = sys.stdin.read().split()
    # TODO: xử lý và print kết quả

main()
`,
};

export const LANG_PREF_KEY = "pref:lang"; // ngôn ngữ làm bài ưu tiên (localStorage)
