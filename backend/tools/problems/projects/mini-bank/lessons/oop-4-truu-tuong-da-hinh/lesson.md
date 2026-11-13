### Task

- **`abstract class Shape`**: `public abstract double area()`, `public abstract String name()`, `public String describe()` returning `<name>: <area>` (2 decimals, `Locale.ROOT`).
- **`Circle extends Shape`** (`Circle`, radius `r`).
- **`Rectangle extends Shape`** (`Rectangle`, `w × h`).
- **`Square extends Rectangle`** (`Square`, side `a`).

`Main` reads one shape per line until end of input: `CIRCLE r`, `RECT w h`, `SQUARE a` (non-negative reals). Store them in one `List<Shape>`, print `describe()` for each, then `Largest: <describe() of the largest>` (ties: the first one). With no shapes, print only `No shapes`.

### Example

Input:

```
CIRCLE 1
RECT 2 3
SQUARE 2.5
```

Output:

```
Circle: 3.14
Rectangle: 6.00
Square: 6.25
Largest: Square: 6.25
```

### Grading

- Structure: `Shape` is abstract with abstract `area()` and `name()`; `Circle`, `Rectangle` extend `Shape`; `Square` extends `Rectangle`.
- Stdout compared line by line, extra spaces ignored.
