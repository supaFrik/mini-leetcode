Finish the command table and the exit code. All tests of stages 1–4 run here.

### Requirements

| Command | Output |
|---------|--------|
| `HELP` | the command table (`printHelp()`) |
| `REPORT <n>` | `n` lines `Report line <i> - total <total>`, `i = 1..n`, total without separators; `n = 0` prints nothing |
| `EXIT [code]` | `Bye!`, then exit with `code` (default `0`) |

### Example

Input:

```
1
Minh
C
10
HELP
REPORT 3
REPORT 0
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Accounts to open: Owner #1: Type (S = savings, C = checking): Initial balance: -> Opened #1 CHECKING     Minh                    10
Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>
          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]
> Report line 1 - total 10
Report line 2 - total 10
Report line 3 - total 10
> > Bye!
```
