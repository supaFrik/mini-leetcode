Given a `beginWord`, an `endWord` and a dictionary `wordList`, build a sequence that starts at `beginWord` and ends at `endWord`, where neighbouring words differ in exactly one letter and every word after `beginWord` is in `wordList`. Return the number of words in the **shortest** such sequence, or `0` if none exists.

**Input format**

- Line 1: `beginWord` and `endWord`, separated by a space.
- Line 2: the words of `wordList`, separated by single spaces.

**Output format**

Print the length of the shortest sequence (counting both `beginWord` and `endWord`), or `0`.

**Example 1:**

```
Input: beginWord = "cold", endWord = "warm", wordList = ["cord", "card", "ward", "warm", "wore"]
Output: 5
Explanation: cold, cord, card, ward, warm.
```

**Example 2:**

```
Input: beginWord = "cold", endWord = "warm", wordList = ["cord", "card", "ward"]
Output: 0
Explanation: `"warm"` is not in `wordList`.
```

**Constraints:**

- `1 <= beginWord.length <= 10`
- `endWord.length == beginWord.length`, `beginWord != endWord`
- `1 <= wordList.length <= 5000`, all words distinct
- All words use lowercase English letters and have the same length as `beginWord`.
