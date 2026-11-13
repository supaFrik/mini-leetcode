## Abstract classes

"A shape" has no area of its own; only a concrete circle or rectangle does. Such a class is `abstract`:

```java
public abstract class Shape {
    public abstract double area();     // no body: subclasses must implement it
    public abstract String name();

    public String describe() {
        return String.format(Locale.ROOT, "%s: %.2f", name(), area());
    }
}

new Shape();   // compile error
```

- A class with an `abstract` method must be `abstract`.
- An abstract class can still have fields, constructors and regular methods.
- A concrete subclass implements every abstract method.

```java
public class Circle extends Shape {
    private final double r;
    public Circle(double r) { this.r = r; }

    @Override public double area() { return Math.PI * r * r; }
    @Override public String name() { return "Circle"; }
}
```

## Declared type and actual type

```java
Shape s = new Circle(1);   // declared: Shape, actual: Circle
s.area();                  // runs Circle.area()
```

The compiler uses the declared type to decide which methods may be called. At run time the JVM uses the actual type to pick the implementation (dynamic dispatch). One loop handles every shape, including ones added later:

```java
for (Shape s : shapes) {
    System.out.println(s.describe());
}
```

## Polymorphism instead of instanceof

```java
if (s instanceof Circle c) { ... } else if (s instanceof Rectangle r) { ... }   // edit for every new shape
total += s.area();                                                             // a new shape is a new class
```

## Template method

`describe()` fixes the format in the parent; subclasses fill in `name()` and `area()`. In Mini Bank, `Account.withdraw()` lives in the parent and calls the abstract `canWithdraw()`.

## Common mistakes

- Implementing an abstract method with a different signature.
- Choosing a formula with `instanceof`.
- `String.format("%.2f")` without a `Locale`: some locales print `3,14`.
