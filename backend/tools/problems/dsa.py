"""Sinh testcase cho các bài DSA trong seed (CHỈ DÙNG CHO DEV). Cách làm giống backend/tools/two-sum/gen.py.

Mỗi bài có một hàm solve_* (lời giải chuẩn, tự chứa import để gửi thẳng lên trình chấm Python)
và một danh sách testcase. Kết quả đúng sinh từ lời giải chuẩn, không tính tay.
Random cố định seed nên chạy lại ra đúng các file cũ.

Run: python backend/tools/problems/dsa.py [slug ...]   (ghi vào backend/tools/<slug>/cases/)
Nạp lên server: backend/tools/seed-two-sum-testcases.ps1 -Slug <slug> ...
"""
import random
import string
import sys
from pathlib import Path

rng = random.Random(20261104)


# ---------------------------------------------------------------- lời giải chuẩn

def solve_valid_parentheses(text):
    s = text.split("\n")[0].strip()
    pair = {")": "(", "]": "[", "}": "{"}
    stack = []
    for ch in s:
        if ch in pair:
            if not stack or stack.pop() != pair[ch]:
                return "false"
        else:
            stack.append(ch)
    return "false" if stack else "true"


def solve_reverse_linked_list(text):
    return " ".join(reversed(text.split("\n")[0].split()))


def solve_binary_search(text):
    lines = text.split("\n")
    nums = [int(x) for x in lines[0].split()]
    target = int(lines[1])
    lo, hi = 0, len(nums) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        if nums[mid] == target:
            return str(mid)
        if nums[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return "-1"


def solve_longest_substring(text):
    s = text.split("\n")[0].strip()
    last = {}
    best = start = 0
    for i, ch in enumerate(s):
        if last.get(ch, -1) >= start:
            start = last[ch] + 1
        last[ch] = i
        best = max(best, i - start + 1)
    return str(best)


def solve_number_of_islands(text):
    from collections import deque
    lines = text.split("\n")
    m, n = map(int, lines[0].split())
    grid = [list(lines[1 + r].strip()) for r in range(m)]
    count = 0
    for r in range(m):
        for c in range(n):
            if grid[r][c] != "1":
                continue
            count += 1
            grid[r][c] = "0"
            q = deque([(r, c)])
            while q:
                y, x = q.popleft()
                for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                    if 0 <= ny < m and 0 <= nx < n and grid[ny][nx] == "1":
                        grid[ny][nx] = "0"
                        q.append((ny, nx))
    return str(count)


def solve_coin_change(text):
    lines = text.split("\n")
    coins = [int(x) for x in lines[0].split()]
    amount = int(lines[1])
    inf = amount + 1
    dp = [0] + [inf] * amount
    for a in range(1, amount + 1):
        for c in coins:
            if c <= a and dp[a - c] + 1 < dp[a]:
                dp[a] = dp[a - c] + 1
    return str(dp[amount] if dp[amount] <= amount else -1)


def solve_top_k_frequent(text):
    from collections import Counter
    lines = text.split("\n")
    nums = [int(x) for x in lines[0].split()]
    k = int(lines[1])
    top = sorted(Counter(nums).items(), key=lambda kv: -kv[1])[:k]
    return " ".join(str(v) for v in sorted(v for v, _ in top))


def solve_course_schedule(text):
    from collections import deque
    lines = text.split("\n")
    n, m = map(int, lines[0].split())
    indeg = [0] * n
    adj = [[] for _ in range(n)]
    for i in range(m):
        a, b = map(int, lines[1 + i].split())
        adj[b].append(a)
        indeg[a] += 1
    q = deque(i for i in range(n) if indeg[i] == 0)
    done = 0
    while q:
        u = q.popleft()
        done += 1
        for v in adj[u]:
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)
    return "true" if done == n else "false"


def solve_merge_k_sorted_lists(text):
    import heapq
    lines = text.split("\n")
    k = int(lines[0])
    lists = [[int(x) for x in lines[1 + i].split()] for i in range(k)]
    return " ".join(str(v) for v in heapq.merge(*lists))


def solve_trapping_rain_water(text):
    h = [int(x) for x in text.split("\n")[0].split()]
    lo, hi = 0, len(h) - 1
    left_max = right_max = water = 0
    while lo < hi:
        if h[lo] < h[hi]:
            left_max = max(left_max, h[lo])
            water += left_max - h[lo]
            lo += 1
        else:
            right_max = max(right_max, h[hi])
            water += right_max - h[hi]
            hi -= 1
    return str(water)


def solve_word_ladder(text):
    from collections import defaultdict, deque
    lines = text.split("\n")
    begin, end = lines[0].split()
    words = set(lines[1].split())
    if end not in words:
        return "0"
    buckets = defaultdict(list)
    for w in words | {begin}:
        for i in range(len(w)):
            buckets[w[:i] + "*" + w[i + 1:]].append(w)
    seen = {begin}
    q = deque([(begin, 1)])
    while q:
        w, d = q.popleft()
        if w == end:
            return str(d)
        for i in range(len(w)):
            key = w[:i] + "*" + w[i + 1:]
            for nxt in buckets.pop(key, []):
                if nxt not in seen:
                    seen.add(nxt)
                    q.append((nxt, d + 1))
    return "0"


# ---------------------------------------------------------------- dữ liệu

def line(nums):
    return " ".join(map(str, nums))


def brackets(n, valid):
    out, stack = [], []
    for i in range(n):
        if stack and (rng.random() < 0.5 or len(stack) >= n - i):
            out.append({"(": ")", "[": "]", "{": "}"}[stack.pop()])
        else:
            ch = rng.choice("([{")
            out.append(ch)
            stack.append(ch)
    s = "".join(out)
    return s if valid else s[:-1] + ("]" if s[-1] != "]" else ")")


def grid(m, n, p):
    return f"{m} {n}\n" + "\n".join("".join("1" if rng.random() < p else "0" for _ in range(n)) for _ in range(m))


def top_k_case(distinct, k, low, high):
    values = rng.sample(range(-10000, 10001), distinct)
    freq = [rng.randint(high + 1, high + 60) for _ in range(k)] + [rng.randint(low, high) for _ in range(distinct - k)]
    nums = [v for v, f in zip(values, freq) for _ in range(f)]
    rng.shuffle(nums)
    return f"{line(nums)}\n{k}"


def dag(n, m, cycle):
    order = list(range(n))
    rng.shuffle(order)
    edges = set()
    while len(edges) < m:
        i, j = sorted(rng.sample(range(n), 2))
        edges.add((order[j], order[i]))  # học order[j] cần học order[i] trước
    edges = list(edges)
    if cycle:
        a, b = edges[0]
        edges.append((b, a))
    rng.shuffle(edges)
    return f"{n} {len(edges)}\n" + "\n".join(f"{a} {b}" for a, b in edges)


def sorted_lists(sizes):
    lists = [sorted(rng.randint(-10000, 10000) for _ in range(s)) for s in sizes]
    return f"{len(lists)}\n" + "\n".join(line(x) for x in lists)


def ladder(length, steps, noise):
    letters = string.ascii_lowercase
    w = "".join(rng.choice(letters) for _ in range(length))
    begin, path = w, []
    for _ in range(steps):
        i = rng.randrange(length)
        w = w[:i] + rng.choice(letters.replace(w[i], "")) + w[i + 1:]
        path.append(w)
    words = set(path) | {"".join(rng.choice(letters) for _ in range(length)) for _ in range(noise)}
    words.discard(begin)
    words = sorted(words)
    rng.shuffle(words)
    return f"{begin} {path[-1]}\n{' '.join(words)}"


# (slug, lời giải, [(tên, input, ẩn?)]) — 2 ví dụ đầu khớp Example 1, 2 trong đề.
PROBLEMS = [
    ("valid-parentheses", solve_valid_parentheses, [
        ("example-1", "([]{})", False),
        ("example-2", "{[}]", False),
        ("single-close", ")", True),
        ("only-opens", "((((", True),
        ("long-valid", brackets(10000, True), True),
        ("long-invalid", brackets(10000, False), True),
    ]),
    ("reverse-linked-list", solve_reverse_linked_list, [
        ("example-1", "4 9 -2 7", False),
        ("example-2", "1 2", False),
        ("empty", "", True),
        ("single", "42", True),
        ("duplicates", "3 3 -1 3 0 0", True),
        ("long", line(rng.randint(-5000, 5000) for _ in range(5000)), True),
    ]),
    ("binary-search", solve_binary_search, [
        ("example-1", "-3 0 4 8 15\n8", False),
        ("example-2", "2 5 9\n4", False),
        ("single-found", "7\n7", True),
        ("single-missing", "7\n-7", True),
        ("long-last", (lambda a: f"{line(a)}\n{a[-1]}")(sorted(rng.sample(range(-10000, 10001), 10000))), True),
        ("long-below-min", (lambda a: f"{line(a)}\n{a[0] - 1}")(sorted(rng.sample(range(-9999, 10001), 10000))), True),
    ]),
    ("longest-substring-without-repeating-characters", solve_longest_substring, [
        ("example-1", "xyzxyzz", False),
        ("example-2", "bbbb", False),
        ("empty", "", True),
        ("single", "Q", True),
        ("all-printable", "".join(rng.sample([chr(c) for c in range(33, 127)], 94)), True),
        ("long-mixed", "".join(rng.choice(string.ascii_letters + string.digits + "!@#$%") for _ in range(50000)), True),
    ]),
    ("number-of-islands", solve_number_of_islands, [
        ("example-1", "4 5\n11000\n11000\n00100\n00011", False),
        ("example-2", "3 3\n111\n010\n111", False),
        ("single-land", "1 1\n1", True),
        ("all-water", grid(300, 300, 0.0), True),
        ("checkerboard", "300 300\n" + "\n".join("".join("1" if (r + c) % 2 == 0 else "0" for c in range(300)) for r in range(300)), True),
        ("random", grid(300, 300, 0.45), True),
    ]),
    ("coin-change", solve_coin_change, [
        ("example-1", "2 5 10\n27", False),
        ("example-2", "4\n7", False),
        ("zero-amount", "3 7\n0", True),
        ("only-ones", "1\n10000", True),
        ("huge-coin", "2147483647 3\n9999", True),
        ("even-coins-odd-amount", "2 4 6 8 10 12 14 16 18 20 22 24\n9999", True),
        ("random-12", f"{line(rng.sample(range(1, 500), 12))}\n10000", True),
    ]),
    ("top-k-frequent-elements", solve_top_k_frequent, [
        ("example-1", "5 5 5 2 2 9\n2", False),
        ("example-2", "-1\n1", False),
        ("all-distinct-values", "-3 -3 -3 8 8 0\n3", True),
        ("negatives", "-10000 -10000 -2 -2 -2 10000 7 7 7 7\n2", True),
        ("long", top_k_case(1000, 25, 1, 80), True),
        ("long-k1", top_k_case(5000, 1, 1, 15), True),
    ]),
    ("course-schedule", solve_course_schedule, [
        ("example-1", "3 2\n1 0\n2 1", False),
        ("example-2", "2 2\n1 0\n0 1", False),
        ("no-prerequisites", "2000 0", True),
        ("long-chain", "2000 1999\n" + "\n".join(f"{i + 1} {i}" for i in range(1999)), True),
        ("long-cycle", "2000 2000\n" + "\n".join(f"{(i + 1) % 2000} {i}" for i in range(2000)), True),
        ("random-dag", dag(2000, 5000, False), True),
        ("random-dag-plus-back-edge", dag(2000, 4999, True), True),
    ]),
    ("merge-k-sorted-lists", solve_merge_k_sorted_lists, [
        ("example-1", "3\n1 4 6\n-2 3\n0 5 9 10", False),
        ("example-2", "2\n\n7", False),
        ("no-lists", "0", True),
        ("all-empty", "3\n\n\n", True),
        ("single-list", sorted_lists([10]), True),
        ("many-lists", sorted_lists([10] * 1000), True),
    ]),
    ("trapping-rain-water", solve_trapping_rain_water, [
        ("example-1", "3 0 2 0 4", False),
        ("example-2", "5 2 1 3", False),
        ("single-bar", "7", True),
        ("increasing", line(range(20000)), True),
        ("deep-valley", "100000 0 0 0 0 100000", True),
        ("long-random", line(rng.randint(0, 100000) for _ in range(20000)), True),
    ]),
    ("word-ladder", solve_word_ladder, [
        ("example-1", "cold warm\ncord card ward warm wore", False),
        ("example-2", "cold warm\ncord card ward", False),
        ("one-step", "a c\na b c", True),
        ("unreachable", "abc xyz\nabd xbz xyz", True),
        ("long-chain", ladder(5, 40, 4000), True),
        ("long-noise-missing-end", (lambda t: t.split("\n")[0].split()[0] + " zzzzz\n" + t.split("\n")[1])(ladder(5, 20, 4500)), True),
    ]),
]

if __name__ == "__main__":
    only = set(sys.argv[1:])
    root = Path(__file__).resolve().parent.parent
    for slug, solve, cases in PROBLEMS:
        if only and slug not in only:
            continue
        out = root / slug / "cases"
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*"):
            old.unlink()
        for idx, (name, inp, hidden) in enumerate(cases, 1):
            stem = f"{idx:02d}-{name}{'-hidden' if hidden else ''}"
            (out / f"{stem}.in").write_text(inp + "\n", newline="\n")
            (out / f"{stem}.out").write_text(solve(inp + "\n") + "\n", newline="\n")
        print(slug, [solve(c[1] + "\n")[:20] for c in cases])
