A singly linked list is given as the sequence of its node values from head to tail. Reverse the list and output the values of the reversed list from its new head to its new tail.

**Input format**

- Line 1: the node values separated by single spaces. The line is empty when the list has no nodes.

**Output format**

Print the reversed values on one line, separated by single spaces. Print an empty line for an empty list.

**Example 1:**

```
Input: head = [4, 9, -2, 7]
Output: 7 -2 9 4
Explanation: The last node becomes the new head.
```

**Example 2:**

```
Input: head = [1, 2]
Output: 2 1
Explanation: Two nodes simply swap places.
```

**Constraints:**

- The number of nodes is in the range `[0, 5000]`.
- `-5000 <= Node.val <= 5000`
