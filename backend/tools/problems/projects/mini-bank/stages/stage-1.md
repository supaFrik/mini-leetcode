Open accounts at startup, deposit, show balances. Each later stage reruns the tests of earlier stages.

### Requirements

1. **`Account`**: id (auto-increment from `1`), type, owner, balance. **No public fields** (checked by reflection).
   - `public long deposit(long amount)` returns the new balance; `amount <= 0` throws `IllegalArgumentException`.
   - `public long getBalance()`.
   - `toString()` returns `String.format("#%d %-12s %-10s %,15d", id, type, owner, balance)`; type is `SAVINGS` or `CHECKING`.
2. **`Bank`**: holds the accounts; `open(type, owner, initial)` with `type` `S` (SAVINGS) or `C` (CHECKING); `find(id)`; `totalBalance()`.
3. **`Main`** reads `System.in` with `Scanner`:
   - Print `=== Mini Bank ===`.
   - Print `Accounts to open: ` (no newline) and read `n`. For each account `i = 1..n` print `Owner #i: `, `Type (S = savings, C = checking): `, `Initial balance: ` (no newlines), read each value, then print `-> Opened ` + account.
   - Print the command table with the provided `printHelp()`.
   - Loop: print `> ` (no newline), read one command:

| Command | Output |
|---------|--------|
| `DEPOSIT <id> <amount>` | `New balance: <balance>` (`%,d`) |
| `BALANCE <id>` | the account (`toString()`) |
| `LIST` | one account per line, in opening order |
| `EXIT` | `Bye!`, then stop |

   - At end of input: print an empty line, then `End of input. Total assets: <total>` (`%,d`).

Errors go to `System.err` and the program continues. Only stdout is compared, line by line, ignoring extra spaces and blank lines.

### Example

Input:

```
2
An
S
100000
Binh
C
0
DEPOSIT 2 50000
BALANCE 1
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Accounts to open: Owner #1: Type (S = savings, C = checking): Initial balance: -> Opened #1 SAVINGS      An                 100,000
Owner #2: Type (S = savings, C = checking): Initial balance: -> Opened #2 CHECKING     Binh                     0
Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> New balance: 50,000
> #1 SAVINGS      An                 100,000
> #1 SAVINGS      An                 100,000
#2 CHECKING     Binh                50,000
> Bye!
```

Prompts use `print`, so typed input does not appear in stdout. After `nextInt()` / `nextLong()`, call `nextLine()` to consume the rest of the line.
