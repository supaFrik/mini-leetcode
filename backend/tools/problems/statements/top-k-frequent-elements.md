Given an integer array `nums` and an integer `k`, find the `k` values that occur most often. The input guarantees that this set of `k` values is unique.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `k`.

**Output format**

Print the `k` values in **increasing order**, separated by single spaces.

**Example 1:**

```
Input: nums = [5, 5, 5, 2, 2, 9], k = 2
Output: 2 5
Explanation: 5 appears three times and 2 twice; they are printed in increasing order.
```

**Example 2:**

```
Input: nums = [-1], k = 1
Output: -1
Explanation: There is only one value.
```

**Constraints:**

- `1 <= nums.length <= 10⁵`
- `-10⁴ <= nums[i] <= 10⁴`
- `1 <= k <=` the number of distinct values in `nums`
- The answer is unique.
