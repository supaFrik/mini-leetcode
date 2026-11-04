Model a family of shapes that all report their area through one common method, then print the area of each shape in the input.

**Suggested classes**

- `Shape`: an abstract class or interface with `double area()`.
- `Circle`, `Rectangle`, `Triangle`: subclasses that store their own dimensions and implement `area()`.
- A factory or parser that builds the right `Shape` from each command.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: the number of commands `q`.
- Next `q` lines: one command each, tokens separated by single spaces.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `CIRCLE <r>`: a circle with radius `r`.
- `RECTANGLE <w> <h>`: a rectangle.
- `TRIANGLE <a> <b> <c>`: a triangle with these side lengths; compute its area with Heron's formula.
- For each shape print its area with exactly two digits after the decimal point (for example `12.57`).
- Print `INVALID` if any dimension is `<= 0`, or if the triangle sides break the strict triangle inequality (`a + b > c` for every pair).

**Example 1:**

```
Input:
3
CIRCLE 2
RECTANGLE 3 4
TRIANGLE 3 4 5

Output:
12.57
12.00
6.00
```

π · 2² ≈ 12.566 rounds to 12.57.

**Example 2:**

```
Input:
3
CIRCLE 0
TRIANGLE 1 2 3
RECTANGLE 5 -2

Output:
INVALID
INVALID
INVALID
```

A zero radius, a flat triangle (1 + 2 = 3) and a negative side are all invalid.

**Constraints:**

- `1 <= q <= 2 * 10⁴`
- `-20 <= every dimension <= 1000`
- Use `double` and the standard value of π (`Math.PI` or `math.pi`).
