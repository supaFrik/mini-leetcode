"""Sinh testcase cho bài Two Sum (CHỈ DÙNG CHO DEV).

Đầu vào: dòng 1 là mảng (cách nhau bởi dấu cách), dòng 2 là target.
Đầu ra: hai chỉ số tăng dần, dạng "[i, j]". Kết quả lấy từ lời giải O(n), không tính tay.
Random cố định seed nên chạy lại ra đúng các file cũ.

Run: python backend/tools/two-sum/gen.py   (ghi vào backend/tools/two-sum/cases/)
"""
import random
from collections import Counter
from pathlib import Path

LIMIT = 10**9


def solve(nums, target):
    seen = {}
    for j, x in enumerate(nums):
        if target - x in seen:
            return seen[target - x], j
        seen[x] = j
    raise ValueError("no answer")


def pair_count(nums, target):
    """Số cặp chỉ số (i < j) có tổng bằng target, để bảo đảm đáp án duy nhất."""
    c = Counter(nums)
    total = 0
    for x, k in c.items():
        y = target - x
        if y == x:
            total += k * (k - 1)
        elif y in c:
            total += k * c[y]
    return total // 2


def long_case(rng, n, i, j):
    """Mảng n phần tử, đáp án duy nhất tại (i, j)."""
    while True:
        nums = [rng.randint(-LIMIT, LIMIT) for _ in range(n)]
        target = rng.randint(-LIMIT, LIMIT)
        nums[i] = rng.randint(max(-LIMIT, target - LIMIT), min(LIMIT, target + LIMIT))
        nums[j] = target - nums[i]
        if pair_count(nums, target) == 1:
            return nums, target


rng = random.Random(20261104)
CASES = [
    # (tên, nums, target, ẩn?)
    ("example-1", [3, 8, 11, 5], 13, False),
    ("example-2", [-4, 7, 2, 9], 5, False),
    ("example-3", [6, 6], 12, False),
    ("negatives", [-10, -3, -7, -1, -20], -8, True),
    ("zeros", [0, 4, 0, 9], 0, True),
    ("duplicates", [5, 1, 5, 3], 10, True),
    ("large-values", [1000000000, -999999999, 7, -1000000000, 999999999], 1, True),
    ("min-length-extremes", [-1000000000, 1000000000], 0, True),
    ("long-ends", *long_case(rng, 10000, 0, 9999), True),
    ("long-middle", *long_case(rng, 9999, 4321, 5678), True),
]

out = Path(__file__).parent / "cases"
out.mkdir(exist_ok=True)
for idx, (name, nums, target, hidden) in enumerate(CASES, 1):
    assert 2 <= len(nums) <= 10**4 and all(-LIMIT <= x <= LIMIT for x in nums) and -LIMIT <= target <= LIMIT, name
    assert pair_count(nums, target) == 1, f"{name}: answer must be unique"
    i, j = solve(nums, target)
    stem = f"{idx:02d}-{name}{'-hidden' if hidden else ''}"
    (out / f"{stem}.in").write_text(" ".join(map(str, nums)) + "\n" + str(target) + "\n", newline="\n")
    (out / f"{stem}.out").write_text(f"[{i}, {j}]\n", newline="\n")
    print(stem, f"[{i}, {j}]")
