-- ============================================================
-- seed-problems.sql  (CHỈ DÙNG CHO MÔI TRƯỜNG DEV)
-- 20 bài PUBLISHED (12 DSA + 8 OOP, đủ EASY/MEDIUM/HARD) kèm chủ đề,
-- để có dữ liệu xem trang /problems.
--
-- Run (PowerShell, đứng ở thư mục gốc repo):
--   Get-Content backend\tools\seed-problems.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode
--
-- Chạy lại bao nhiêu lần cũng được:
--   * Bài nhận diện theo slug (UNIQUE). Bài đã có sẽ được cập nhật, không nhân đôi.
--   * Chỉ bài do tài khoản `seed_author` tạo mới bị cập nhật / gán lại chủ đề.
--     Nếu DB đã có bài thật trùng slug, bài đó được giữ nguyên.
--
-- LƯU Ý: các bài này CHƯA CÓ TESTCASE nên chưa nộp và chấm được.
-- Muốn chấm thật thì thêm testcase qua trang Admin hoặc API
-- POST /api/v1/problems/{id}/test-cases.
-- ============================================================

-- 1. Tác giả của dữ liệu seed. password_hash không phải bcrypt nên tài khoản này không đăng nhập được.
INSERT INTO users (username, email, password_hash, role, status)
VALUES ('seed_author', 'seed_author@dev.local', '!seed-account-cannot-login', 'INSTRUCTOR', 'BLOCKED')
ON DUPLICATE KEY UPDATE username = username;

SET @seed_author := (SELECT id FROM users WHERE username = 'seed_author');

-- 2. Bài tập. seq quyết định thứ tự hiển thị (trang /problems sắp mới nhất trước,
--    nên created_at = NOW() - seq phút để bài seq 1 nằm trên cùng).
DROP TEMPORARY TABLE IF EXISTS seed_problem_rows;
CREATE TEMPORARY TABLE seed_problem_rows (
    seq INT NOT NULL,
    slug VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    problem_type VARCHAR(30) NOT NULL,
    description TEXT NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO seed_problem_rows (seq, slug, title, difficulty, problem_type, description) VALUES
-- ---------- DSA (ALGORITHMIC) ----------
( 1, 'two-sum', 'Two Sum', 'EASY', 'ALGORITHMIC',
 'You are given an integer array `nums` and an integer `target`. Find the two different positions `i` and `j` such that `nums[i] + nums[j] == target`, and return them.

Every input has **exactly one** valid pair, and the same element cannot be used twice. Print the two indices in **increasing order**, in the form `[i, j]`.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `target`.

**Example 1:**

```
Input: nums = [3, 8, 11, 5], target = 13
Output: [1, 3]
Explanation: nums[1] + nums[3] = 8 + 5 = 13.
```

**Example 2:**

```
Input: nums = [-4, 7, 2, 9], target = 5
Output: [0, 3]
Explanation: nums[0] + nums[3] = -4 + 9 = 5.
```

**Example 3:**

```
Input: nums = [6, 6], target = 12
Output: [0, 1]
Explanation: The two equal values sit at different positions, so they form a valid pair.
```

**Constraints:**

- `2 <= nums.length <= 10⁴`
- `-10⁹ <= nums[i] <= 10⁹`
- `-10⁹ <= target <= 10⁹`
- Exactly one valid answer exists.

**Follow-up:** A pair of nested loops solves this in `O(n²)` time. Can you do it in `O(n)` time?'),
( 2, 'valid-parentheses', 'Valid Parentheses', 'EASY', 'ALGORITHMIC',
 'You are given a string `s` made only of the characters `(`, `)`, `[`, `]`, `{` and `}`. The string is **valid** when every opening bracket is closed by a bracket of the same type, and brackets close in the reverse order they were opened.

**Input format**

- Line 1: the string `s`.

**Output format**

Print `true` if `s` is valid, otherwise print `false` (lowercase).

**Example 1:**

```
Input: s = "([]{})"
Output: true
Explanation: Every bracket is closed by its own type, innermost first.
```

**Example 2:**

```
Input: s = "{[}]"
Output: false
Explanation: `[` is still open when `}` arrives.
```

**Constraints:**

- `1 <= s.length <= 10⁴`
- `s` contains only `()[]{}`.'),
( 3, 'reverse-linked-list', 'Reverse Linked List', 'EASY', 'ALGORITHMIC',
 'A singly linked list is given as the sequence of its node values from head to tail. Reverse the list and output the values of the reversed list from its new head to its new tail.

**Input format**

- Line 1: the node values separated by single spaces. The line is empty when the list has no nodes.

**Output format**

Print the reversed values on one line, separated by single spaces. Print an empty line for an empty list.

**Example 1:**

```
Input: head = [4, 9, -2, 7]
Output: 7 -2 9 4
Explanation: The last node becomes the new head.
```

**Example 2:**

```
Input: head = [1, 2]
Output: 2 1
Explanation: Two nodes simply swap places.
```

**Constraints:**

- The number of nodes is in the range `[0, 5000]`.
- `-5000 <= Node.val <= 5000`'),
( 4, 'binary-search', 'Binary Search', 'EASY', 'ALGORITHMIC',
 'You are given an array `nums` of **distinct** integers sorted in increasing order, and an integer `target`. Return the index of `target` in `nums`, or `-1` if it is not present. Aim for `O(log n)` time.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `target`.

**Output format**

Print the 0-based index, or `-1`.

**Example 1:**

```
Input: nums = [-3, 0, 4, 8, 15], target = 8
Output: 3
Explanation: `nums[3] = 8`.
```

**Example 2:**

```
Input: nums = [2, 5, 9], target = 4
Output: -1
Explanation: 4 does not appear in `nums`.
```

**Constraints:**

- `1 <= nums.length <= 10⁴`
- `-10⁴ <= nums[i], target <= 10⁴`
- All values in `nums` are distinct and sorted in increasing order.'),
( 5, 'longest-substring-without-repeating-characters', 'Longest Substring Without Repeating Characters', 'MEDIUM', 'ALGORITHMIC',
 'Given a string `s`, find the length of the longest **substring** (a contiguous block of characters) in which no character appears twice.

**Input format**

- Line 1: the string `s`. The line is empty when `s` is empty.

**Output format**

Print the length as an integer.

**Example 1:**

```
Input: s = "xyzxyzz"
Output: 3
Explanation: The answer is `"xyz"`; any longer block repeats a letter.
```

**Example 2:**

```
Input: s = "bbbb"
Output: 1
Explanation: Every block longer than one character contains two `"b"`.
```

**Constraints:**

- `0 <= s.length <= 5 * 10⁴`
- `s` contains printable ASCII characters except the space (codes 33 to 126).'),
( 6, 'number-of-islands', 'Number of Islands', 'MEDIUM', 'ALGORITHMIC',
 'You are given an `m x n` grid where `1` is land and `0` is water. An **island** is a group of land cells connected horizontally or vertically (not diagonally). Count the islands. Everything outside the grid is water.

**Input format**

- Line 1: two integers `m` and `n`.
- Next `m` lines: each row of the grid as a string of `n` characters, each `0` or `1`.

**Output format**

Print the number of islands.

**Example 1:**

```
Input: m = 4, n = 5
11000
11000
00100
00011
Output: 3
Explanation: The top-left block, the single centre cell and the bottom-right pair.
```

**Example 2:**

```
Input: m = 3, n = 3
111
010
111
Output: 1
Explanation: The middle cell links the top and bottom rows into one island.
```

**Constraints:**

- `1 <= m, n <= 300`
- Each cell is `0` or `1`.'),
( 7, 'coin-change', 'Coin Change', 'MEDIUM', 'ALGORITHMIC',
 'You are given the coin values `coins` (you have an unlimited number of each) and a target `amount`. Return the **fewest** coins whose values add up to exactly `amount`, or `-1` if no combination works.

**Input format**

- Line 1: the coin values, separated by single spaces.
- Line 2: the integer `amount`.

**Output format**

Print the minimum number of coins, or `-1`.

**Example 1:**

```
Input: coins = [2, 5, 10], amount = 27
Output: 4
Explanation: 27 = 10 + 10 + 5 + 2.
```

**Example 2:**

```
Input: coins = [4], amount = 7
Output: -1
Explanation: No multiple of 4 equals 7.
```

**Constraints:**

- `1 <= coins.length <= 12`
- `1 <= coins[i] <= 2³¹ - 1`, all values distinct
- `0 <= amount <= 10⁴`'),
( 8, 'top-k-frequent-elements', 'Top K Frequent Elements', 'MEDIUM', 'ALGORITHMIC',
 'Given an integer array `nums` and an integer `k`, find the `k` values that occur most often. The input guarantees that this set of `k` values is unique.

**Input format**

- Line 1: the elements of `nums`, separated by single spaces.
- Line 2: the integer `k`.

**Output format**

Print the `k` values in **increasing order**, separated by single spaces.

**Example 1:**

```
Input: nums = [5, 5, 5, 2, 2, 9], k = 2
Output: 2 5
Explanation: 5 appears three times and 2 twice; they are printed in increasing order.
```

**Example 2:**

```
Input: nums = [-1], k = 1
Output: -1
Explanation: There is only one value.
```

**Constraints:**

- `1 <= nums.length <= 10⁵`
- `-10⁴ <= nums[i] <= 10⁴`
- `1 <= k <=` the number of distinct values in `nums`
- The answer is unique.'),
( 9, 'course-schedule', 'Course Schedule', 'MEDIUM', 'ALGORITHMIC',
 'There are `numCourses` courses labelled `0` to `numCourses - 1`. A prerequisite pair `a b` means you must finish course `b` before course `a`. Decide whether it is possible to finish every course.

**Input format**

- Line 1: two integers `numCourses` and `m` (the number of pairs).
- Next `m` lines: two integers `a` and `b`.

**Output format**

Print `true` if every course can be finished, otherwise `false`.

**Example 1:**

```
Input: numCourses = 3, pairs = [[1, 0], [2, 1]]
Output: true
Explanation: Take course 0, then 1, then 2.
```

**Example 2:**

```
Input: numCourses = 2, pairs = [[1, 0], [0, 1]]
Output: false
Explanation: Each course waits for the other, so neither can start.
```

**Constraints:**

- `1 <= numCourses <= 2000`
- `0 <= m <= 5000`
- `0 <= a, b < numCourses`, `a != b`
- All pairs are distinct.'),
(10, 'merge-k-sorted-lists', 'Merge k Sorted Lists', 'HARD', 'ALGORITHMIC',
 'You are given `k` linked lists, each sorted in increasing order. Merge them into a single list that is also sorted in increasing order and output its values.

**Input format**

- Line 1: the integer `k`.
- Next `k` lines: the values of one list, separated by single spaces. A line is empty when that list is empty.

**Output format**

Print the merged values on one line, separated by single spaces. Print an empty line when there are no values at all.

**Example 1:**

```
Input: lists = [[1, 4, 6], [-2, 3], [0, 5, 9, 10]]
Output: -2 0 1 3 4 5 6 9 10
Explanation: All nine values in increasing order.
```

**Example 2:**

```
Input: lists = [[], [7]]
Output: 7
Explanation: Empty lists contribute nothing.
```

**Constraints:**

- `0 <= k <= 10⁴`
- The total number of values is at most `10⁴`.
- `-10⁴ <= value <= 10⁴`
- Each list is sorted in increasing order.'),
(11, 'trapping-rain-water', 'Trapping Rain Water', 'HARD', 'ALGORITHMIC',
 'Bars of width 1 stand side by side with heights `height[0..n-1]`. After it rains, water collects in the dips between taller bars. Compute the total units of water held.

**Input format**

- Line 1: the heights, separated by single spaces.

**Output format**

Print the total amount of trapped water. It can exceed 32-bit range, so use a 64-bit integer.

**Example 1:**

```
Input: height = [3, 0, 2, 0, 4]
Output: 7
Explanation: The dips hold 3 + 1 + 3 units.
```

**Example 2:**

```
Input: height = [5, 2, 1, 3]
Output: 3
Explanation: Water rises to height 3 over the bars of height 2 and 1.
```

**Constraints:**

- `1 <= n <= 2 * 10⁴`
- `0 <= height[i] <= 10⁵`'),
(12, 'word-ladder', 'Word Ladder', 'HARD', 'ALGORITHMIC',
 'Given a `beginWord`, an `endWord` and a dictionary `wordList`, build a sequence that starts at `beginWord` and ends at `endWord`, where neighbouring words differ in exactly one letter and every word after `beginWord` is in `wordList`. Return the number of words in the **shortest** such sequence, or `0` if none exists.

**Input format**

- Line 1: `beginWord` and `endWord`, separated by a space.
- Line 2: the words of `wordList`, separated by single spaces.

**Output format**

Print the length of the shortest sequence (counting both `beginWord` and `endWord`), or `0`.

**Example 1:**

```
Input: beginWord = "cold", endWord = "warm", wordList = ["cord", "card", "ward", "warm", "wore"]
Output: 5
Explanation: cold, cord, card, ward, warm.
```

**Example 2:**

```
Input: beginWord = "cold", endWord = "warm", wordList = ["cord", "card", "ward"]
Output: 0
Explanation: `"warm"` is not in `wordList`.
```

**Constraints:**

- `1 <= beginWord.length <= 10`
- `endWord.length == beginWord.length`, `beginWord != endWord`
- `1 <= wordList.length <= 5000`, all words distinct
- All words use lowercase English letters and have the same length as `beginWord`.'),
-- ---------- OOP (OOP_GRINDING) ----------
(13, 'bank-account-class', 'Bank Account Class', 'EASY', 'ALGORITHMIC', 
 'Build a small bank that manages several accounts. Each account keeps its balance private and only changes it through deposits and withdrawals that pass validation.

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
- Balances can exceed 32-bit range; use a 64-bit integer.'),
(14, 'shape-area-hierarchy', 'Shape Area Hierarchy', 'EASY', 'ALGORITHMIC', 
 'Model a family of shapes that all report their area through one common method, then print the area of each shape in the input.

**Suggested classes**

- `Shape`: an abstract class or interface with `double area()`.
- `Circle`, `Rectangle`, `Triangle`: subclasses that store their own dimensions and implement `area()`.
- A factory or parser that builds the right `Shape` from each command.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: the number of commands `q`.
- Next `q` lines: one command each, tokens separated by single spaces.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `CIRCLE <r>`: a circle with radius `r`.
- `RECTANGLE <w> <h>`: a rectangle.
- `TRIANGLE <a> <b> <c>`: a triangle with these side lengths; compute its area with Heron''s formula.
- For each shape print its area with exactly two digits after the decimal point (for example `12.57`).
- Print `INVALID` if any dimension is `<= 0`, or if the triangle sides break the strict triangle inequality (`a + b > c` for every pair).

**Example 1:**

```
Input:
3
CIRCLE 2
RECTANGLE 3 4
TRIANGLE 3 4 5

Output:
12.57
12.00
6.00
```

π · 2² ≈ 12.566 rounds to 12.57.

**Example 2:**

```
Input:
3
CIRCLE 0
TRIANGLE 1 2 3
RECTANGLE 5 -2

Output:
INVALID
INVALID
INVALID
```

A zero radius, a flat triangle (1 + 2 = 3) and a negative side are all invalid.

**Constraints:**

- `1 <= q <= 2 * 10⁴`
- `-20 <= every dimension <= 1000`
- Use `double` and the standard value of π (`Math.PI` or `math.pi`).'),
(15, 'library-management-system', 'Library Management System', 'MEDIUM', 'ALGORITHMIC', 
 'Run a small library. Members borrow and return books, and each member type has its own loan limit.

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
- `-10 <= copies <= 100`'),
(16, 'parking-lot-design', 'Parking Lot Design', 'MEDIUM', 'ALGORITHMIC', 
 'Run a parking lot with small (`S`), medium (`M`) and large (`L`) spots. Each vehicle type fits only some spot sizes, and every parked vehicle gets a ticket.

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
- Plates and ticket ids are 1 to 20 letters or digits.'),
(17, 'multi-client-echo-server', 'Multi-Client Echo Server', 'MEDIUM', 'OOP_GRINDING',
 'Write a TCP server that echoes every line back to its sender and serves several clients at the same time.'),
(18, 'file-transfer-over-tcp', 'File Transfer over TCP', 'MEDIUM', 'OOP_GRINDING',
 'Send a file from a client to a server over a socket, then verify its size and checksum on arrival.'),
(19, 'tcp-chat-room', 'TCP Chat Room', 'HARD', 'OOP_GRINDING',
 'Build a chat server where clients join with a nickname and every message is broadcast to all connected clients.'),
(20, 'event-bus-observer', 'Event Bus with Observers', 'HARD', 'ALGORITHMIC', 
 'Implement a publish/subscribe event bus. Subscribers register for a topic and are notified, in the order they subscribed, whenever a message is published on that topic.

**Suggested classes**

- `Subscriber`: an observer with a name and an `onEvent(topic, message)` method.
- `EventBus`: keeps an ordered list of subscribers per topic and notifies them on publish.

> The judge only compares what your program prints. Class design is not graded automatically, but the suggested classes are the intended way to solve it.

**Input format**

- Line 1: the number of commands `q`.
- Next `q` lines: one command each, tokens separated by single spaces.

**Commands and output** (print exactly one line per command unless stated otherwise)

- `SUBSCRIBE <name> <topic>`: print `OK`, or `ERROR DUPLICATE` if `name` already subscribes to `topic`.
- `UNSUBSCRIBE <name> <topic>`: print `OK`, or `ERROR NOT_SUBSCRIBED`.
- `PUBLISH <topic> <message>`: `message` is the rest of the line. Print one line `<name> <- <topic>: <message>` for every subscriber of `topic`, in subscription order. If there is none, print `NO_SUBSCRIBERS`.
- Subscribing again after unsubscribing puts the name at the end of the order.

**Example 1:**

```
Input:
5
SUBSCRIBE ann orders
SUBSCRIBE bob orders
PUBLISH orders order 42 shipped
UNSUBSCRIBE ann orders
PUBLISH orders order 43 shipped

Output:
OK
OK
ann <- orders: order 42 shipped
bob <- orders: order 42 shipped
OK
bob <- orders: order 43 shipped
```

Both subscribers get the first message; only `bob` gets the second.

**Example 2:**

```
Input:
4
PUBLISH news hello
SUBSCRIBE ann news
SUBSCRIBE ann news
UNSUBSCRIBE bob news

Output:
NO_SUBSCRIBERS
OK
ERROR DUPLICATE
ERROR NOT_SUBSCRIBED
```

Publishing to an empty topic is not an error.

**Constraints:**

- `1 <= q <= 5000`
- Names and topics are 1 to 20 lowercase letters, digits or `_`.
- A message has 1 to 4 words of lowercase letters or digits.');

INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status,
                      created_by, created_at, problem_type)
SELECT r.title, r.slug, r.description, r.difficulty, 1000, 256, 'PUBLISHED',
       @seed_author, NOW() - INTERVAL r.seq MINUTE, r.problem_type
FROM seed_problem_rows r
ORDER BY r.seq
ON DUPLICATE KEY UPDATE
    title        = IF(problems.created_by = @seed_author, VALUES(title), problems.title),
    description  = IF(problems.created_by = @seed_author, VALUES(description), problems.description),
    difficulty   = IF(problems.created_by = @seed_author, VALUES(difficulty), problems.difficulty),
    problem_type = IF(problems.created_by = @seed_author, VALUES(problem_type), problems.problem_type),
    status       = IF(problems.created_by = @seed_author, 'PUBLISHED', problems.status);

-- Ba bài socket chưa chấm tự động được (trình chấm OOP không đọc testcase của bài), nên ẩn khỏi người dùng.
UPDATE problems SET status = 'ARCHIVED'
WHERE created_by = @seed_author
  AND slug IN ('multi-client-echo-server', 'file-transfer-over-tcp', 'tcp-chat-room');

-- 3. Chủ đề. Xoá rồi gán lại cho đúng các bài của seed_author, nên chạy lại không bị trùng.
DROP TEMPORARY TABLE IF EXISTS seed_tag_rows;
CREATE TEMPORARY TABLE seed_tag_rows (
    slug VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    tag VARCHAR(50) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO seed_tag_rows (slug, sort_order, tag) VALUES
('two-sum', 0, 'Array'), ('two-sum', 1, 'Hash Map'),
('valid-parentheses', 0, 'Stack'), ('valid-parentheses', 1, 'String'),
('reverse-linked-list', 0, 'Linked List'),
('binary-search', 0, 'Array'), ('binary-search', 1, 'Binary Search'),
('longest-substring-without-repeating-characters', 0, 'String'),
('longest-substring-without-repeating-characters', 1, 'Hash Map'),
('longest-substring-without-repeating-characters', 2, 'Sliding Window'),
('number-of-islands', 0, 'Graph'), ('number-of-islands', 1, 'Matrix'),
('coin-change', 0, 'Dynamic Programming'),
('top-k-frequent-elements', 0, 'Array'), ('top-k-frequent-elements', 1, 'Hash Map'), ('top-k-frequent-elements', 2, 'Heap'),
('course-schedule', 0, 'Graph'), ('course-schedule', 1, 'Topological Sort'),
('merge-k-sorted-lists', 0, 'Linked List'), ('merge-k-sorted-lists', 1, 'Heap'),
('trapping-rain-water', 0, 'Array'), ('trapping-rain-water', 1, 'Two Pointers'),
('word-ladder', 0, 'Graph'), ('word-ladder', 1, 'String'),
('bank-account-class', 0, 'Encapsulation'),
('shape-area-hierarchy', 0, 'Inheritance'), ('shape-area-hierarchy', 1, 'Polymorphism'),
('library-management-system', 0, 'Inheritance'), ('library-management-system', 1, 'Polymorphism'),
('parking-lot-design', 0, 'Encapsulation'), ('parking-lot-design', 1, 'Design Patterns'),
('multi-client-echo-server', 0, 'Socket'), ('multi-client-echo-server', 1, 'Multithreading'),
('file-transfer-over-tcp', 0, 'Socket'), ('file-transfer-over-tcp', 1, 'Streams'),
('tcp-chat-room', 0, 'Socket'), ('tcp-chat-room', 1, 'Multithreading'),
('event-bus-observer', 0, 'Design Patterns'), ('event-bus-observer', 1, 'Interfaces');

DELETE pt FROM problem_tags pt
JOIN problems p ON p.id = pt.problem_id
WHERE p.created_by = @seed_author;

INSERT INTO problem_tags (problem_id, sort_order, tag)
SELECT p.id, t.sort_order, t.tag
FROM seed_tag_rows t
JOIN problems p ON p.slug = t.slug AND p.created_by = @seed_author;

DROP TEMPORARY TABLE IF EXISTS seed_problem_rows;
DROP TEMPORARY TABLE IF EXISTS seed_tag_rows;

-- 4. Kiểm tra nhanh.
SELECT p.problem_type, p.difficulty, COUNT(*) AS problems
FROM problems p WHERE p.created_by = @seed_author
GROUP BY p.problem_type, p.difficulty ORDER BY p.problem_type, FIELD(p.difficulty, 'EASY', 'MEDIUM', 'HARD');

SELECT COUNT(*) AS seeded_problems_without_testcases
FROM problems p
WHERE p.created_by = @seed_author
  AND NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.problem_id = p.id);
