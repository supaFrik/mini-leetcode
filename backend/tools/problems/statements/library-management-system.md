Run a small library. Members borrow and return books, and each member type has its own loan limit.

**Suggested classes**

- `Book`: an id and the number of copies on the shelf.
- `Member`: an abstract class with `int loanLimit()`; `Student` (limit 2) and `Teacher` (limit 5) extend it and track the books they hold.
- `Library`: stores books and members and applies the rules below.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: the number of commands `q`.
- Next `q` lines: one command each, tokens separated by single spaces.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `ADD_BOOK <bookId> <copies>`: print `ERROR INVALID` if `copies <= 0`, `ERROR DUPLICATE` if the book exists, otherwise `OK`.
- `ADD_MEMBER <memberId> <type>`: `type` is `STUDENT` or `TEACHER`. Print `ERROR INVALID` for any other type, `ERROR DUPLICATE` if the member exists, otherwise `OK`.
- `BORROW <memberId> <bookId>`: check in this order and print the first that applies: `ERROR NO_MEMBER`, `ERROR NO_BOOK`, `ERROR ALREADY_BORROWED` (the member already holds this book), `ERROR LIMIT` (the member holds as many books as the limit), `ERROR NO_COPY` (no copy on the shelf). Otherwise take one copy and print `OK`.
- `RETURN <memberId> <bookId>`: print `ERROR NO_MEMBER`, `ERROR NO_BOOK` or `ERROR NOT_BORROWED` (in this order), otherwise put the copy back and print `OK`.
- `AVAILABLE <bookId>`: print the copies on the shelf, or `ERROR NO_BOOK`.

**Example 1:**

```
Input:
6
ADD_BOOK B1 1
ADD_MEMBER ann STUDENT
BORROW ann B1
AVAILABLE B1
RETURN ann B1
AVAILABLE B1

Output:
OK
OK
OK
0
OK
1
```

The only copy leaves the shelf and comes back.

**Example 2:**

```
Input:
6
ADD_BOOK B1 0
ADD_MEMBER tom GUEST
BORROW tom B1
ADD_BOOK B2 1
ADD_MEMBER tom TEACHER
RETURN tom B2

Output:
ERROR INVALID
ERROR INVALID
ERROR NO_MEMBER
OK
OK
ERROR NOT_BORROWED
```

Invalid adds create nothing, so `tom` does not exist until he is added as a teacher.

**Constraints:**

- `1 <= q <= 2 * 10⁴`
- Ids are 1 to 20 letters or digits.
- `-10 <= copies <= 100`
