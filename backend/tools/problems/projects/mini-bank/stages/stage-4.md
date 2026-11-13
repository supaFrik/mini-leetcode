Move the business rules into `Bank`; `Main` only reads commands and prints results.

### Requirements

1. **`Bank` encapsulates** its accounts (private field) and has:
   - `public Account open(String type, String owner, long initialBalance)`: a type other than `S` / `C` (case-insensitive) throws `IllegalArgumentException` and **uses no id**.
   - `public Account find(int id)`: unknown id throws `IllegalArgumentException`.
   - `public void transfer(int fromId, int toId, long amount) throws InsufficientFundsException`.
2. New command `OPEN <S|C> <balance> <name>`: the name is the rest of the line. Success prints `-> Opened ` + account; a bad type is an error on `System.err`.
3. A bad type at startup is handled the same way: report it and skip that account.

### Example

Input:

```
0
OPEN S 70000 Le Thi Hong Nhung
OPEN C 5000 Pham Quang
LIST
TRANSFER 1 2 15000
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Accounts to open: Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> -> Opened #1 SAVINGS      Le Thi Hong Nhung          70,000
> -> Opened #2 CHECKING     Pham Quang           5,000
> #1 SAVINGS      Le Thi Hong Nhung          70,000
#2 CHECKING     Pham Quang           5,000
> Transferred.
> #1 SAVINGS      Le Thi Hong Nhung          55,000
#2 CHECKING     Pham Quang          20,000
> Bye!
```

`%-10s` does not cut names longer than 10 characters.
