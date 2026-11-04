You are given a string `s` made only of the characters `(`, `)`, `[`, `]`, `{` and `}`. The string is **valid** when every opening bracket is closed by a bracket of the same type, and brackets close in the reverse order they were opened.

**Input format**

- Line 1: the string `s`.

**Output format**

Print `true` if `s` is valid, otherwise print `false` (lowercase).

**Example 1:**

```
Input: s = "([]{})"
Output: true
Explanation: Every bracket is closed by its own type, innermost first.
```

**Example 2:**

```
Input: s = "{[}]"
Output: false
Explanation: `[` is still open when `}` arrives.
```

**Constraints:**

- `1 <= s.length <= 10⁴`
- `s` contains only `()[]{}`.
