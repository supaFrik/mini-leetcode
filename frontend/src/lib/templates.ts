import type { Language } from "@/types/api";

export const MONACO_LANG: Record<Language, string> = { JAVA: "java", PYTHON: "python", CPP: "cpp" };
export const FILE_NAME: Record<Language, string> = { JAVA: "Solution.java", PYTHON: "solution.py", CPP: "solution.cpp" };

// Judge chạy chương trình đọc stdin / ghi stdout (xem ví dụ Scanner trong spec),
// nên template là chương trình hoàn chỉnh, KHÔNG phải hàm kiểu LeetCode.
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
    // TODO
    return 0;
}
`,
};