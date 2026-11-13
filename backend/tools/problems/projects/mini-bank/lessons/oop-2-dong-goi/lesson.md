### Task

Write **`Wallet`**:

- `private final String owner`, `private long balance` (starts at 0). No public fields, no setters.
- `public Wallet(String owner)`.
- `public void deposit(long amount)`: `amount <= 0` throws `IllegalArgumentException("Amount must be positive")`.
- `public void pay(long amount)`: `amount <= 0` throws `IllegalArgumentException("Amount must be positive")`; `amount > balance` throws `IllegalArgumentException("Insufficient funds")`. On error the balance is unchanged.
- `public long getBalance()`, `public String getOwner()`.

`Main` reads the owner on line 1, then one command per line until end of input:

| Command | Output |
|---------|--------|
| `DEPOSIT <amount>` | `OK, balance: <balance>` |
| `PAY <amount>` | `OK, balance: <balance>` |
| `SHOW` | `<owner>: <balance>` |
| error in `DEPOSIT` / `PAY` | `Error: <exception message>` |

### Example

Input:

```
Lan
DEPOSIT 100000
PAY 30000
PAY 500000
DEPOSIT 0
SHOW
```

Output:

```
OK, balance: 100000
OK, balance: 70000
Error: Insufficient funds
Error: Amount must be positive
Lan: 70000
```

### Grading

- Structure: no public field in `Wallet`; `public void deposit(long)`, `public void pay(long)`, `public long getBalance()`.
- Stdout compared line by line, extra spaces ignored.
