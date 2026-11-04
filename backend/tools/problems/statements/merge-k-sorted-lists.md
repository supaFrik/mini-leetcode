You are given `k` linked lists, each sorted in increasing order. Merge them into a single list that is also sorted in increasing order and output its values.

**Input format**

- Line 1: the integer `k`.
- Next `k` lines: the values of one list, separated by single spaces. A line is empty when that list is empty.

**Output format**

Print the merged values on one line, separated by single spaces. Print an empty line when there are no values at all.

**Example 1:**

```
Input: lists = [[1, 4, 6], [-2, 3], [0, 5, 9, 10]]
Output: -2 0 1 3 4 5 6 9 10
Explanation: All nine values in increasing order.
```

**Example 2:**

```
Input: lists = [[], [7]]
Output: 7
Explanation: Empty lists contribute nothing.
```

**Constraints:**

- `0 <= k <= 10⁴`
- The total number of values is at most `10⁴`.
- `-10⁴ <= value <= 10⁴`
- Each list is sorted in increasing order.
