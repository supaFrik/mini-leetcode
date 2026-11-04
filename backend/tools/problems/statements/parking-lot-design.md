Run a parking lot with small (`S`), medium (`M`) and large (`L`) spots. Each vehicle type fits only some spot sizes, and every parked vehicle gets a ticket.

**Suggested classes**

- `Vehicle`: an abstract class with the spot sizes it fits; `Motorcycle`, `Car` and `Bus` extend it.
- `ParkingLot`: free spots per size and the active tickets.
- `Ticket`: the ticket id, the plate and the spot size used.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: three integers, the number of `S`, `M` and `L` spots.
- Line 2: the number of commands `q`.
- Next `q` lines: one command each.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `PARK <plate> <type>`: `type` is `MOTORCYCLE` (fits `S`, `M`, `L`), `CAR` (fits `M`, `L`) or `BUS` (fits `L`). Print `ERROR INVALID` for any other type, `ERROR DUPLICATE` if this plate is already parked, `FULL` if no fitting spot is free. Otherwise use the **smallest** free size that fits and print `T<n> <size>`, where `n` counts successful parks starting from 1.
- `LEAVE <ticketId>`: free the spot and print `OK`, or print `ERROR UNKNOWN_TICKET` if the ticket is not active.
- `FREE <size>`: print the number of free spots of size `S`, `M` or `L`, or `ERROR INVALID` for any other size.

**Example 1:**

```
Input:
1 1 1
4
PARK m1 MOTORCYCLE
PARK c1 CAR
PARK c2 CAR
PARK b1 BUS

Output:
T1 S
T2 M
T3 L
FULL
```

The second car takes the large spot, so the bus finds nothing.

**Example 2:**

```
Input:
0 1 0
5
PARK c1 CAR
LEAVE T1
LEAVE T1
PARK c1 TRUCK
FREE M

Output:
T1 M
OK
ERROR UNKNOWN_TICKET
ERROR INVALID
1
```

A ticket can be used only once.

**Constraints:**

- `0 <=` spots of each size `<= 1000`
- `1 <= q <= 2 * 10⁴`
- Plates and ticket ids are 1 to 20 letters or digits.
