Two account types with different rules.

### Requirements

1. **`Account` is `abstract`** (drop the stage 1 type field):
   - `protected abstract boolean canWithdraw(long amount)`
   - `public abstract void endOfMonth()`
   - `public abstract String getType()`
   - `public long withdraw(long amount)`: `amount <= 0` fails; `!canWithdraw(amount)` fails and **keeps the balance**; otherwise subtracts and returns the new balance.
2. **`SavingsAccount extends Account`** (`SAVINGS`): at least **50,000** must remain after a withdrawal; month end adds **0.5%** interest, `Math.round(balance * 0.005)`.
3. **`CheckingAccount extends Account`** (`CHECKING`): the balance after a withdrawal may not go below **-500,000**; month end subtracts a **10,000** fee (may go below the limit).
4. New commands:

| Command | Output |
|---------|--------|
| `WITHDRAW <id> <amount>` | success: `New balance: <balance>`; failure: nothing on stdout |
| `MONTH` | call `endOfMonth()` on **every** account, print `Month-end interest and fees applied.` |

### Example

Input:

```
1
Chi
S
80000
WITHDRAW 1 30001
WITHDRAW 1 30000
WITHDRAW 1 1
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Accounts to open: Owner #1: Type (S = savings, C = checking): Initial balance: -> Opened #1 SAVINGS      Chi                 80,000
Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> > New balance: 50,000
> > #1 SAVINGS      Chi                 50,000
> Bye!
```

The first and last withdrawals would leave less than 50,000, so only the prompt is printed.
