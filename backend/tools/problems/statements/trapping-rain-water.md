Bars of width 1 stand side by side with heights `height[0..n-1]`. After it rains, water collects in the dips between taller bars. Compute the total units of water held.

**Input format**

- Line 1: the heights, separated by single spaces.

**Output format**

Print the total amount of trapped water. It can exceed 32-bit range, so use a 64-bit integer.

**Example 1:**

```
Input: height = [3, 0, 2, 0, 4]
Output: 7
Explanation: The dips hold 3 + 1 + 3 units.
```

**Example 2:**

```
Input: height = [5, 2, 1, 3]
Output: 3
Explanation: Water rises to height 3 over the bars of height 2 and 1.
```

**Constraints:**

- `1 <= n <= 2 * 10⁴`
- `0 <= height[i] <= 10⁵`
