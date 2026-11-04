You are given an `m x n` grid where `1` is land and `0` is water. An **island** is a group of land cells connected horizontally or vertically (not diagonally). Count the islands. Everything outside the grid is water.

**Input format**

- Line 1: two integers `m` and `n`.
- Next `m` lines: each row of the grid as a string of `n` characters, each `0` or `1`.

**Output format**

Print the number of islands.

**Example 1:**

```
Input: m = 4, n = 5
11000
11000
00100
00011
Output: 3
Explanation: The top-left block, the single centre cell and the bottom-right pair.
```

**Example 2:**

```
Input: m = 3, n = 3
111
010
111
Output: 1
Explanation: The middle cell links the top and bottom rows into one island.
```

**Constraints:**

- `1 <= m, n <= 300`
- Each cell is `0` or `1`.
