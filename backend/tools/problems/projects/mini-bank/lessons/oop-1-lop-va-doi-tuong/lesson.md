### Task

Write **`Customer`**:

- `private static int nextId = 1`.
- Private fields `id` and `name`.
- `public Customer(String name)`: takes `id` from the counter, then increments it.
- `getId()`, `getName()`.
- `public static int count()`: number of customers created.
- `toString()` returns `Customer#<id>: <name>`.

`Main` reads:

- Line 1: `n` (`0 <= n <= 1000`).
- `n` lines: one name each (may contain spaces).

Print each customer, then `Total customers: <count>` from `Customer.count()`.

### Example

Input:

```
3
Ann
Bob
Carol Lee
```

Output:

```
Customer#1: Ann
Customer#2: Bob
Customer#3: Carol Lee
Total customers: 3
```

### Grading

- Structure: `public Customer(String)`, `private static int nextId`, `public static int count()`, `public String toString()`.
- Stdout compared line by line, extra spaces ignored.
