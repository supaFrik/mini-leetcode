import type { Language } from "@/types/api";

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
