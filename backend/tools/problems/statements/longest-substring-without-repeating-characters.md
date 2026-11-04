Given a string `s`, find the length of the longest **substring** (a contiguous block of characters) in which no character appears twice.

**Input format**

- Line 1: the string `s`. The line is empty when `s` is empty.

**Output format**

Print the length as an integer.

**Example 1:**

```
Input: s = "xyzxyzz"
Output: 3
Explanation: The answer is `"xyz"`; any longer block repeats a letter.
```

**Example 2:**

```
Input: s = "bbbb"
Output: 1
Explanation: Every block longer than one character contains two `"b"`.
```

**Constraints:**

- `0 <= s.length <= 5 * 10⁴`
- `s` contains printable ASCII characters except the space (codes 33 to 126).
