## Is-a

A savings account is an account. When "B is an A" holds, `B extends A`: B gets A's fields and methods and adds or changes some.

```java
public class Employee {
    private final String name;
    protected final long baseSalary;

    public Employee(String name, long baseSalary) {
        this.name = name;
        this.baseSalary = baseSalary;
    }

    public long salary() { return baseSalary; }
}

public class Manager extends Employee {
    private final long bonus;

    public Manager(String name, long baseSalary, long bonus) {
        super(name, baseSalary);   // must be the first statement
        this.bonus = bonus;
    }
}
```

A class extends one class. Every class extends `Object`. Private fields of the parent exist in the child but are not accessible directly.

## super(...)

A subclass constructor calls the parent constructor first. Without an explicit `super(...)` Java inserts `super()`; if the parent has no empty constructor, the code does not compile.

## Overriding

Same name, same parameters, new behavior:

```java
@Override
public long salary() {
    return super.salary() + bonus;
}
```

The method that runs is chosen by the object's actual type at run time:

```java
Employee e = new Manager("Ha", 20_000_000, 5_000_000);
e.salary();   // 25000000
```

- Different parameters is overloading, not overriding.
- Access may not be narrowed (`public` stays `public`).
- `final`, `static` and `private` methods are not overridden.

## When not to inherit

- "B has an A" (a bank has accounts): use a field.
- Reusing a few helper methods: call them, do not extend.
- A subclass that has to disable a parent method: the is-a relation is wrong.

## Common mistakes

- Missing `super(...)` when the parent has no empty constructor.
- Changing the parameters while "overriding".
- Copying parent code instead of calling `super.method()`.
