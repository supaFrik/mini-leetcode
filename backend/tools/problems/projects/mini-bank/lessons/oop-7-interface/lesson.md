### Task

- **`interface Command`**: `String name()`, `String run(String args)`.
- **`EchoCommand`** (`ECHO`): returns `args` with runs of spaces collapsed.
- **`AddCommand`** (`ADD`): sums the integers in `args` (`0` if none); a non-integer token throws `IllegalArgumentException("Not a number: <token>")`.
- **`UpperCommand`** (`UPPER`): returns `args` in upper case (`Locale.ROOT`).

All three implement `Command`. `Main` registers them in a `Map<String, Command>` and reads `<NAME> <args...>` lines until end of input:

- `HELP`: `Commands: <registered names, sorted, separated by ", ">`.
- a registered name: print `run(args)`; on `IllegalArgumentException` print `Error: <message>`.
- anything else: `Unknown command: <NAME>`.

### Example

Input:

```
HELP
ECHO hello   world
ADD 1 2 3
ADD 4 x
UPPER mini bank
PING
```

Output:

```
Commands: ADD, ECHO, UPPER
hello world
6
Error: Not a number: x
MINI BANK
Unknown command: PING
```

### Grading

- Structure: `Command` is an interface with `name()` and `run(String)`; `EchoCommand`, `AddCommand`, `UpperCommand` implement it.
- Stdout compared line by line, extra spaces ignored.
