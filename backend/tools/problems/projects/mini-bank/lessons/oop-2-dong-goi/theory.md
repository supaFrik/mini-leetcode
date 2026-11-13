## The problem

```java
public class Account {
    public long balance;
}

account.balance = -1_000_000;   // compiles, breaks the business rule
```

Encapsulation: fields are `private`, and state changes only through the class's own methods. Every rule is checked in one place.

## Access modifiers

| Modifier | Visible to |
|----------|------------|
| `private` | the same class |
| (none) | the same package |
| `protected` | the same package and subclasses |
| `public` | everyone |

Fields are `private`. Methods other classes call are `public`. Helpers are `private`.

## Invariants

An invariant always holds for a valid object, for example "the balance is never negative". The constructor establishes it; every method keeps it.

```java
public void withdraw(long amount) {
    if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
    if (amount > balance) throw new IllegalArgumentException("Insufficient funds");
    balance -= amount;
}
```

Check first, change state after. On bad input, throw and change nothing. The caller decides what to do:

```java
try {
    account.withdraw(500);
} catch (IllegalArgumentException e) {
    System.out.println("Error: " + e.getMessage());
}
```

## Getters, not setters

A getter reads. A setter writes anything and reopens the door:

```java
public void setBalance(long b) { balance = b; }   // breaks the invariant
```

Replace setters with business methods (`deposit`, `withdraw`). Mark fields that never change `final`.

## Common mistakes

- Public fields "for convenience".
- A setter for every field.
- Validating in `Main` instead of in the class.
- Changing state before validating.
