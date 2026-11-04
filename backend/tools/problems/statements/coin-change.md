You are given the coin values `coins` (you have an unlimited number of each) and a target `amount`. Return the **fewest** coins whose values add up to exactly `amount`, or `-1` if no combination works.

**Input format**

- Line 1: the coin values, separated by single spaces.
- Line 2: the integer `amount`.

**Output format**

Print the minimum number of coins, or `-1`.

**Example 1:**

```
Input: coins = [2, 5, 10], amount = 27
Output: 4
Explanation: 27 = 10 + 10 + 5 + 2.
```

**Example 2:**

```
Input: coins = [4], amount = 7
Output: -1
Explanation: No multiple of 4 equals 7.
```

**Constraints:**

- `1 <= coins.length <= 12`
- `1 <= coins[i] <= 2³¹ - 1`, all values distinct
- `0 <= amount <= 10⁴`
