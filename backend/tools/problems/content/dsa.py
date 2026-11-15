"""DSA problem content (time limits include ~0.5 s JVM start-up in a fresh sandbox): English statements (fixed template), Java reference solutions, test inputs.

Expected outputs are NOT stored here: rewrite_content.py runs each reference on its inputs.
Each case is (label, stdin, public). The first two public cases become the statement's examples.
"""
import random

R = random.Random(20261114)  # fixed seed -> identical tests on every run


def _nums(n, lo, hi):
    return " ".join(str(R.randint(lo, hi)) for _ in range(n))


def _two_sum_case(n):
    vals = R.sample(range(-10**9, 10**9), n)  # distinct values
    i, j = sorted(R.sample(range(n), 2))
    target = vals[i] + vals[j]
    # keep the answer unique: no other pair may hit the target
    seen = {}
    for k, v in enumerate(vals):
        if target - v in seen and (seen[target - v], k) != (i, j):
            return _two_sum_case(n)
        seen[v] = k
    return " ".join(map(str, vals)) + f"\n{target}\n"


PROBLEMS = [
    {
        "id": 1, "title": "Two Sum", "difficulty": "EASY", "tags": ["Array", "Hash Table"], "timeLimitMs": 2000,
        "problem": "Given an array of integers `nums` and an integer `target`, find the two **different** positions "
                   "whose values add up to `target`.\n\nEvery test has exactly one such pair.",
        "input": "- Line 1: the integers of `nums`, separated by single spaces.\n- Line 2: the integer `target`.",
        "output": "The two zero-based indices in **increasing** order, separated by one space.",
        "explain": {0: "`nums[0] + nums[1] = 2 + 7 = 9`."},
        "constraints": ["`2 <= nums.length <= 10^4`", "`-10^9 <= nums[i] <= 10^9`", "`-2*10^9 <= target <= 2*10^9`",
                        "Exactly one valid pair exists.", "Aim for O(n) time."],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String[] parts = sc.nextLine().trim().split("\\s+");
        long target = Long.parseLong(sc.nextLine().trim());
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            Integer j = seen.get(target - v);
            if (j != null) {
                System.out.println(j + " " + i);
                return;
            }
            seen.putIfAbsent(v, i);
        }
    }
}
''',
        "cases": [("example 1", "2 7 11 15\n9\n", True), ("example 2", "3 2 4\n6\n", True),
                  ("same values", "3 3\n6\n", False), ("negatives", "-1 -2 -3 -4 -5\n-8\n", False),
                  ("pair at the ends", "5 1 2 3 10\n15\n", False), ("large input", _two_sum_case(10000), False)],
    },
    {
        "id": 3, "title": "Palindrome Number", "difficulty": "EASY", "tags": ["Math"], "timeLimitMs": 2000,
        "problem": "Given an integer `x`, decide whether it reads the same forwards and backwards. "
                   "Negative numbers are never palindromes (the minus sign only appears at the front).",
        "input": "- Line 1: the integer `x`.",
        "output": "`true` if `x` is a palindrome, otherwise `false`.",
        "explain": {1: "Read backwards, `-121` becomes `121-`."},
        "constraints": ["`-2^31 <= x <= 2^31 - 1`"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        long x = new Scanner(System.in).nextLong();
        String s = Long.toString(x);
        System.out.println(x >= 0 && new StringBuilder(s).reverse().toString().equals(s));
    }
}
''',
        "cases": [("example 1", "121\n", True), ("example 2", "-121\n", True), ("ends with zero", "10\n", False),
                  ("zero", "0\n", False), ("single digit", "7\n", False), ("long palindrome", "1234554321\n", False),
                  ("max int", "2147483647\n", False)],
    },
    {
        "id": 4, "title": "FizzBuzz", "difficulty": "EASY", "tags": ["Math", "String"], "timeLimitMs": 2000,
        "problem": "For every number `i` from `1` to `n`, print:\n\n- `FizzBuzz` if `i` is divisible by both 3 and 5,\n"
                   "- `Fizz` if `i` is divisible by 3 only,\n- `Buzz` if `i` is divisible by 5 only,\n- otherwise `i` itself.",
        "input": "- Line 1: the integer `n`.",
        "output": "`n` lines, the result for `i = 1, 2, ..., n` in order.",
        "explain": {},
        "constraints": ["`1 <= n <= 10^4`"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        int n = new Scanner(System.in).nextInt();
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            out.append(i % 15 == 0 ? "FizzBuzz" : i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : String.valueOf(i)).append('\n');
        }
        System.out.print(out);
    }
}
''',
        "cases": [("example 1", "5\n", True), ("example 2", "15\n", True), ("n = 1", "1\n", False),
                  ("n = 3", "3\n", False), ("n = 100", "100\n", False), ("n = 10000", "10000\n", False)],
    },
    {
        "id": 5, "title": "Fibonacci Number", "difficulty": "EASY", "tags": ["Math", "Dynamic Programming"], "timeLimitMs": 2000,
        "problem": "The Fibonacci numbers are `F(0) = 0`, `F(1) = 1` and `F(n) = F(n - 1) + F(n - 2)` for `n > 1`. "
                   "Given `n`, print `F(n)`.\n\nA plain recursive solution is too slow for large `n`; "
                   "build the sequence from the bottom up instead.",
        "input": "- Line 1: the integer `n`.",
        "output": "The value of `F(n)`.",
        "explain": {1: "`F(4) = F(3) + F(2) = 2 + 1 = 3`."},
        "constraints": ["`0 <= n <= 90` (the answer fits in a 64-bit `long`)"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        int n = new Scanner(System.in).nextInt();
        long a = 0, b = 1;
        for (int i = 0; i < n; i++) {
            long next = a + b;
            a = b;
            b = next;
        }
        System.out.println(a);
    }
}
''',
        "cases": [("example 1", "2\n", True), ("example 2", "4\n", True), ("n = 0", "0\n", False),
                  ("n = 1", "1\n", False), ("n = 30", "30\n", False), ("n = 90 (needs long)", "90\n", False)],
    },
    {
        "id": 6, "title": "Sum of Array", "difficulty": "EASY", "tags": ["Array"], "timeLimitMs": 2000,
        "problem": "Given an array of integers `nums`, print the sum of all its elements.",
        "input": "- Line 1: the integers of `nums`, separated by single spaces.",
        "output": "The sum of the elements.",
        "explain": {},
        "constraints": ["`1 <= nums.length <= 10^5`", "`-10^9 <= nums[i] <= 10^9` (use a 64-bit `long` for the sum)"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        String[] parts = new Scanner(System.in).nextLine().trim().split("\\s+");
        long sum = 0;
        for (String p : parts) sum += Long.parseLong(p);
        System.out.println(sum);
    }
}
''',
        "cases": [("example 1", "1 2 3 4 5\n", True), ("example 2", "-1 0 1\n", True), ("single element", "42\n", False),
                  ("mixed signs", "-5 10 -2 7\n", False), ("overflows int", "1000000000 1000000000 1000000000\n", False),
                  ("large input", _nums(100000, -10**9, 10**9) + "\n", False)],
    },
    {
        "id": 7, "title": "Find the Maximum", "difficulty": "EASY", "tags": ["Array"], "timeLimitMs": 2000,
        "problem": "Given an array of integers `nums`, print its largest element. Do not sort the array: "
                   "one pass that remembers the best value so far is enough.",
        "input": "- Line 1: the integers of `nums`, separated by single spaces.",
        "output": "The largest value in `nums`.",
        "explain": {},
        "constraints": ["`1 <= nums.length <= 10^5`", "`-10^9 <= nums[i] <= 10^9`"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        String[] parts = new Scanner(System.in).nextLine().trim().split("\\s+");
        long best = Long.MIN_VALUE;
        for (String p : parts) best = Math.max(best, Long.parseLong(p));
        System.out.println(best);
    }
}
''',
        "cases": [("example 1", "3 9 2 7\n", True), ("example 2", "-8 -3 -12\n", True), ("single element", "5\n", False),
                  ("duplicates of the max", "4 4 1 4\n", False), ("max at the end", "1 2 3 1000000000\n", False),
                  ("large input", _nums(100000, -10**9, 10**9) + "\n", False)],
    },
    {
        "id": 8, "title": "Count Vowels", "difficulty": "EASY", "tags": ["String"], "timeLimitMs": 2000,
        "problem": "Given one line of text, count how many of its characters are vowels. The vowels are "
                   "`a`, `e`, `i`, `o`, `u`, in upper or lower case. Every other character (including `y`, digits, "
                   "spaces and punctuation) is not a vowel.",
        "input": "- Line 1: the text (it may contain spaces and punctuation, and may be empty).",
        "output": "The number of vowels.",
        "explain": {0: "`Hello World` has `e`, `o`, `o`."},
        "constraints": ["`0 <= text length <= 10^5`", "The text contains only printable ASCII characters."],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String text = sc.hasNextLine() ? sc.nextLine() : "";
        int count = 0;
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if ("aeiou".indexOf(c) >= 0) count++;
        }
        System.out.println(count);
    }
}
''',
        "cases": [("example 1", "Hello World\n", True), ("example 2", "Rhythm\n", True), ("upper case", "AEIOU aeiou\n", False),
                  ("punctuation", "Q: why? A: no idea!\n", False), ("empty line", "\n", False),
                  ("long text", "Mini LeetCode online judge " * 3000 + "\n", False)],
    },
    {
        "id": 9, "title": "Binary Search", "difficulty": "EASY", "tags": ["Array", "Binary Search"], "timeLimitMs": 2000,
        "problem": "Given an array `nums` sorted in strictly increasing order and a `target`, print the index of "
                   "`target` in `nums`, or `-1` if it is not there.\n\nUse binary search: halve the search range on "
                   "every step (O(log n) comparisons).",
        "input": "- Line 1: the integers of `nums` in increasing order, separated by single spaces.\n- Line 2: the integer `target`.",
        "output": "The zero-based index of `target`, or `-1`.",
        "explain": {0: "`9` is at index 4.", 1: "`2` does not appear."},
        "constraints": ["`1 <= nums.length <= 10^5`", "`-10^9 <= nums[i], target <= 10^9`", "All values in `nums` are distinct."],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String[] parts = sc.nextLine().trim().split("\\s+");
        long target = Long.parseLong(sc.nextLine().trim());
        int lo = 0, hi = parts.length - 1, answer = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = Long.parseLong(parts[mid]);
            if (v == target) { answer = mid; break; }
            if (v < target) lo = mid + 1; else hi = mid - 1;
        }
        System.out.println(answer);
    }
}
''',
        "cases": [("example 1", "-1 0 3 5 9 12\n9\n", True), ("example 2", "-1 0 3 5 9 12\n2\n", True),
                  ("single element found", "7\n7\n", False), ("smaller than all", "1 2 3\n-5\n", False),
                  ("first and last", "10 20 30 40\n40\n", False),
                  ("large input", " ".join(map(str, range(-50000, 100000, 3))) + "\n99998\n", False)],
    },
    {
        "id": 10, "title": "Bubble Sort", "difficulty": "MEDIUM", "tags": ["Array", "Sorting"], "timeLimitMs": 3000,
        "problem": "Sort `nums` in increasing order with **bubble sort**: repeatedly walk through the array and swap "
                   "two neighbours whenever the left one is larger, until a full pass makes no swap.\n\n"
                   "Print the sorted array and the number of swaps bubble sort performed.",
        "input": "- Line 1: the integers of `nums`, separated by single spaces.",
        "output": "- Line 1: the sorted values, separated by single spaces.\n- Line 2: the number of swaps.",
        "explain": {0: "Swaps: (5,1), (5,4), (5,2), (4,2) — 4 in total."},
        "constraints": ["`1 <= nums.length <= 2000`", "`-10^6 <= nums[i] <= 10^6`",
                        "Equal values are never swapped."],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        String[] parts = new Scanner(System.in).nextLine().trim().split("\\s+");
        int[] a = new int[parts.length];
        for (int i = 0; i < a.length; i++) a[i] = Integer.parseInt(parts[i]);
        long swaps = 0;
        boolean swapped = true;
        for (int end = a.length - 1; swapped && end > 0; end--) {
            swapped = false;
            for (int i = 0; i < end; i++) {
                if (a[i] > a[i + 1]) {
                    int t = a[i]; a[i] = a[i + 1]; a[i + 1] = t;
                    swaps++;
                    swapped = true;
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < a.length; i++) out.append(i == 0 ? "" : " ").append(a[i]);
        System.out.println(out);
        System.out.println(swaps);
    }
}
''',
        "cases": [("example 1", "5 1 4 2\n", True), ("example 2", "1 2 3\n", True), ("reversed", "5 4 3 2 1\n", False),
                  ("duplicates", "3 1 3 1 2\n", False), ("single element", "9\n", False),
                  ("2000 elements", _nums(2000, -10**6, 10**6) + "\n", False)],
    },
    {
        "id": 11, "title": "Container With Most Water", "difficulty": "MEDIUM", "tags": ["Array", "Two Pointers"],
        "timeLimitMs": 2000,
        "problem": "There are `n` vertical lines; line `i` stands at position `i` and has height `height[i]`. "
                   "Pick two lines that, together with the x-axis, hold the most water. The amount of water between "
                   "lines `i < j` is `(j - i) * min(height[i], height[j])`.\n\nPrint the largest possible amount. "
                   "Checking every pair is too slow for `n = 10^5`; move two pointers inwards instead.",
        "input": "- Line 1: the `n` heights, separated by single spaces.",
        "output": "The maximum amount of water.",
        "explain": {0: "Lines at positions 1 and 8 (heights 8 and 7): `(8 - 1) * 7 = 49`."},
        "constraints": ["`2 <= n <= 10^5`", "`0 <= height[i] <= 10^4`"],
        "reference": r'''import java.util.*;

public class Solution {
    public static void main(String[] args) {
        String[] parts = new Scanner(System.in).nextLine().trim().split("\\s+");
        int[] h = new int[parts.length];
        for (int i = 0; i < h.length; i++) h[i] = Integer.parseInt(parts[i]);
        int i = 0, j = h.length - 1;
        long best = 0;
        while (i < j) {
            best = Math.max(best, (long) (j - i) * Math.min(h[i], h[j]));
            if (h[i] < h[j]) i++; else j--;
        }
        System.out.println(best);
    }
}
''',
        "cases": [("example 1", "1 8 6 2 5 4 8 3 7\n", True), ("example 2", "1 1\n", True), ("all zero", "0 0 0\n", False),
                  ("increasing", "1 2 3 4 5\n", False), ("tall ends", "10 1 1 1 10\n", False),
                  ("large input", _nums(100000, 0, 10**4) + "\n", False)],
    },
]
