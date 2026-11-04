You are given an array `nums` of **distinct** integers sorted in increasing order, and an integer `target`. Return the index of `target` in `nums`, or `-1` if it is not present. Aim for `O(log n)` time.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `target`.

**Output format**

Print the 0-based index, or `-1`.

**Example 1:**

```
Input: nums = [-3, 0, 4, 8, 15], target = 8
Output: 3
Explanation: `nums[3] = 8`.
```

**Example 2:**

```
Input: nums = [2, 5, 9], target = 4
Output: -1
Explanation: 4 does not appear in `nums`.
```

**Constraints:**

- `1 <= nums.length <= 10⁴`
- `-10⁴ <= nums[i], target <= 10⁴`
- All values in `nums` are distinct and sorted in increasing order.
