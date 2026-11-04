Build a small bank that manages several accounts. Each account keeps its balance private and only changes it through deposits and withdrawals that pass validation.

**Suggested classes**

- `BankAccount`: a private `long balance`, with `deposit(amount)`, `withdraw(amount)` and `getBalance()`; invalid operations are rejected.
- `Bank`: maps account ids to `BankAccount` objects and runs the commands.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: the number of commands `q`.
- Next `q` lines: one command each, tokens separated by single spaces.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `OPEN <id> <initial>`: create an account. Print `OK`, or `ERROR` if the id already exists or `initial < 0`.
- `DEPOSIT <id> <amount>`: print the new balance, or `ERROR` if the account does not exist or `amount <= 0`.
- `WITHDRAW <id> <amount>`: print the new balance, or `ERROR` if the account does not exist, `amount <= 0`, or `amount` is greater than the balance.
- `BALANCE <id>`: print the balance, or `ERROR` if the account does not exist.
- A command that prints `ERROR` changes nothing.

**Example 1:**

```
Input:
4
OPEN alice 100
DEPOSIT alice 50
WITHDRAW alice 30
BALANCE alice

Output:
OK
150
120
120
```

Each successful change prints the new balance.

**Example 2:**

```
Input:
5
OPEN bob 0
WITHDRAW bob 10
DEPOSIT bob -5
BALANCE carol
OPEN bob 7

Output:
OK
ERROR
ERROR
ERROR
ERROR
```

Overdrawing, a negative deposit, an unknown account and a duplicate id are all rejected.

**Constraints:**

- `1 <= q <= 2 * 10⁴`
- Ids are 1 to 20 letters or digits.
- `-10⁹ <= initial, amount <= 2 * 10⁹`
- Balances can exceed 32-bit range; use a 64-bit integer.
