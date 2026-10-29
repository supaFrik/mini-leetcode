import type { Language } from "@/types/api";

export const LANGUAGES: { value: Language; label: string; dot: string }[] = [
  { value: "JAVA", label: "Java", dot: "bg-cyan-400" },
  { value: "PYTHON", label: "Python", dot: "bg-yellow-400" },
  { value: "CPP", label: "C++", dot: "bg-blue-400" },
];
export const MONACO_LANG: Record<Language, string> = { JAVA: "java", PYTHON: "python", CPP: "cpp" };
export const FILE_NAME: Record<Language, string> = { JAVA: "Solution.java", PYTHON: "solution.py", CPP: "solution.cpp" };

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
  CPP: `#include <bits/stdc++.h>
using namespace std;

int main() {
    // TODO: đọc input, in output
    return 0;
}
`,
};

export const LANG_PREF_KEY = "pref:lang"; // ngôn ngữ làm bài ưu tiên (localStorage)
