You are given an integer array `nums` and an integer `target`. Find the two different positions `i` and `j` such that `nums[i] + nums[j] == target`, and return them.

Every input has **exactly one** valid pair, and the same element cannot be used twice. Print the two indices in **increasing order**, in the form `[i, j]`.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `target`.

**Example 1:**

```
Input: nums = [3, 8, 11, 5], target = 13
Output: [1, 3]
Explanation: nums[1] + nums[3] = 8 + 5 = 13.
```

**Example 2:**

```
Input: nums = [-4, 7, 2, 9], target = 5
Output: [0, 3]
Explanation: nums[0] + nums[3] = -4 + 9 = 5.
```

**Example 3:**

```
Input: nums = [6, 6], target = 12
Output: [0, 1]
Explanation: The two equal values sit at different positions, so they form a valid pair.
```

**Constraints:**

- `2 <= nums.length <= 10⁴`
- `-10⁹ <= nums[i] <= 10⁹`
- `-10⁹ <= target <= 10⁹`
- Exactly one valid answer exists.

**Follow-up:** A pair of nested loops solves this in `O(n²)` time. Can you do it in `O(n)` time?
