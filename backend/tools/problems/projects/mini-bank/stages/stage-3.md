A failed withdrawal becomes a checked exception.

### Requirements

1. **`InsufficientFundsException extends Exception`**.
2. `Account.withdraw(long)` declares `throws InsufficientFundsException` and throws it when `!canWithdraw(amount)`.
3. New command `TRANSFER <from> <to> <amount>`: success prints `Transferred.`. An unknown id or insufficient funds changes **no** account: find both accounts first, then withdraw and deposit.
4. Bad input never stops the program. These errors go to `System.err` and nothing is printed on stdout: missing arguments (`WITHDRAW 1`), non-numbers (`BALANCE x`), amount `<= 0`, unknown id, unknown command (`FOO`).

### Example

Input:

```
2
Dung
S
60000
Em
C
0
TRANSFER 1 2 10000
TRANSFER 2 1 400000
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Accounts to open: Owner #1: Type (S = savings, C = checking): Initial balance: -> Opened #1 SAVINGS      Dung                60,000
Owner #2: Type (S = savings, C = checking): Initial balance: -> Opened #2 CHECKING     Em                       0
Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> Transferred.
> Transferred.
> #1 SAVINGS      Dung               450,000
#2 CHECKING     Em                -390,000
> Bye!
```
