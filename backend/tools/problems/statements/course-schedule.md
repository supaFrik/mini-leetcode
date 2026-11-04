There are `numCourses` courses labelled `0` to `numCourses - 1`. A prerequisite pair `a b` means you must finish course `b` before course `a`. Decide whether it is possible to finish every course.

**Input format**

- Line 1: two integers `numCourses` and `m` (the number of pairs).
- Next `m` lines: two integers `a` and `b`.

**Output format**

Print `true` if every course can be finished, otherwise `false`.

**Example 1:**

```
Input: numCourses = 3, pairs = [[1, 0], [2, 1]]
Output: true
Explanation: Take course 0, then 1, then 2.
```

**Example 2:**

```
Input: numCourses = 2, pairs = [[1, 0], [0, 1]]
Output: false
Explanation: Each course waits for the other, so neither can start.
```

**Constraints:**

- `1 <= numCourses <= 2000`
- `0 <= m <= 5000`
- `0 <= a, b < numCourses`, `a != b`
- All pairs are distinct.
