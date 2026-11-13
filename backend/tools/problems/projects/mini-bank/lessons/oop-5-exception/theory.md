## Hierarchy

```
Throwable
├── Error                 JVM problems (OutOfMemoryError) — do not catch
└── Exception             CHECKED: the compiler requires handling
    ├── IOException, your own "extends Exception"
    └── RuntimeException  UNCHECKED
        ├── IllegalArgumentException
        ├── NumberFormatException
        └── NullPointerException
```

- **Checked**: can happen even when the code is correct (missing file, insufficient funds). Callers must `catch` or declare `throws`.
- **Unchecked**: usually a programming error or a broken contract (negative argument, `null`).

## throw and throws

```java
public long withdraw(long amount) throws InsufficientFundsException {
    if (amount > balance) {
        throw new InsufficientFundsException("Insufficient funds");
    }
    balance -= amount;
    return balance;
}
```

`throw` is a statement that leaves the method. `throws` is part of the signature.

## try / catch / finally

```java
try {
    account.withdraw(amount);
} catch (InsufficientFundsException e) {
    System.err.println(e.getMessage());
} catch (NumberFormatException | IllegalArgumentException e) {
    System.err.println("Bad input: " + e.getMessage());
} finally {
    // always runs
}
```

Catch blocks match top to bottom: subclasses before superclasses. Never leave a `catch` empty.

## Custom exceptions

```java
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## Wrapping

Turn a technical error into a business error and keep the cause:

```java
try {
    age = Integer.parseInt(raw);
} catch (NumberFormatException e) {
    throw new InvalidAgeException("Not a number: " + raw, e);
}
```

In a command loop, wrap each command in its own `try / catch` so one bad line does not stop the program.

## Common mistakes

- `catch (Exception e) {}` hides the error.
- `catch (Exception e)` before a more specific catch does not compile.
- Wrapping without passing the cause loses the original stack trace.
