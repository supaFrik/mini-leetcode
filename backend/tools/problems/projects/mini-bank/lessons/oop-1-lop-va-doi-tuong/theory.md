## Class and object

A class describes state (fields) and behavior (methods). An object is one instance, created with `new`.

```java
public class Account {
    long balance;

    void deposit(long amount) {
        balance += amount;
    }
}

Account a = new Account();
Account b = new Account();   // separate balance
a.deposit(100);              // only a changes
```

## Constructor

Runs once on `new`. Same name as the class, no return type.

```java
public class Account {
    private final String owner;
    private long balance;

    public Account(String owner, long initial) {
        this.owner = owner;     // this.owner: field, owner: parameter
        this.balance = initial;
    }
}
```

Without any constructor Java adds an empty one. Declaring a constructor with parameters removes it.

## References

A variable holds a reference, not the object.

```java
Account x = new Account("Ann", 0);
Account y = x;      // same object
y.deposit(50);
x.getBalance();     // 50
```

`==` compares references. Compare strings with `equals`.

## static

A `static` field has one copy for the whole class, which makes it a natural id counter.

```java
private static int nextId = 1;
private final int id;

public Account() {
    this.id = nextId++;
}

public static int created() {
    return nextId - 1;
}
```

A `static` method has no `this` and cannot read instance fields.

## toString

`Object.toString()` prints something like `Account@1b6d3586`. Override it:

```java
@Override
public String toString() {
    return "#" + id + " " + owner;
}
```

`println(account)` calls it. `@Override` makes the compiler check the signature.

## Common mistakes

- `owner = owner;` assigns the parameter to itself; use `this.owner = owner;`.
- A non-static counter gives every object id 1.
- Comparing strings with `==`.
