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
 'Given a string containing only `()[]{}`, decide whether every bracket is closed by the same type in the correct order.'),
( 3, 'reverse-linked-list', 'Reverse Linked List', 'EASY', 'ALGORITHMIC',
 'Given the head of a singly linked list, reverse the list and return the new head.'),
( 4, 'binary-search', 'Binary Search', 'EASY', 'ALGORITHMIC',
 'Given a sorted array and a target, return the index of the target or `-1`. Your solution must run in O(log n).'),
( 5, 'longest-substring-without-repeating-characters', 'Longest Substring Without Repeating Characters', 'MEDIUM', 'ALGORITHMIC',
 'Given a string `s`, find the length of the longest substring that contains no repeated characters.'),
( 6, 'number-of-islands', 'Number of Islands', 'MEDIUM', 'ALGORITHMIC',
 'Given a grid of `1` (land) and `0` (water), count the islands. An island is land connected horizontally or vertically.'),
( 7, 'coin-change', 'Coin Change', 'MEDIUM', 'ALGORITHMIC',
 'Given coin denominations and an amount, return the fewest coins needed to make that amount, or `-1` if it cannot be made.'),
( 8, 'top-k-frequent-elements', 'Top K Frequent Elements', 'MEDIUM', 'ALGORITHMIC',
 'Given an integer array and `k`, return the `k` most frequent elements in any order.'),
( 9, 'course-schedule', 'Course Schedule', 'MEDIUM', 'ALGORITHMIC',
 'Given `n` courses and prerequisite pairs, decide whether it is possible to finish every course.'),
(10, 'merge-k-sorted-lists', 'Merge k Sorted Lists', 'HARD', 'ALGORITHMIC',
 'Merge `k` sorted linked lists into one sorted linked list and return its head.'),
(11, 'trapping-rain-water', 'Trapping Rain Water', 'HARD', 'ALGORITHMIC',
 'Given bar heights of width 1, compute how much water is trapped after it rains.'),
(12, 'word-ladder', 'Word Ladder', 'HARD', 'ALGORITHMIC',
 'Given a start word, an end word and a dictionary, return the length of the shortest transformation sequence changing one letter at a time.'),
-- ---------- OOP (OOP_GRINDING) ----------
(13, 'bank-account-class', 'Bank Account Class', 'EASY', 'OOP_GRINDING',
 'Design a `BankAccount` class with deposit, withdraw and balance. Keep the balance private and reject invalid amounts.'),
(14, 'shape-area-hierarchy', 'Shape Area Hierarchy', 'EASY', 'OOP_GRINDING',
 'Model `Shape`, `Circle`, `Rectangle` and `Triangle` so every shape can report its area through one common method.'),
(15, 'library-management-system', 'Library Management System', 'MEDIUM', 'OOP_GRINDING',
 'Model books, members and loans. Members borrow and return items; different item types have different loan rules.'),
(16, 'parking-lot-design', 'Parking Lot Design', 'MEDIUM', 'OOP_GRINDING',
 'Design a parking lot with spot sizes, vehicle types and a ticketing flow. Keep each responsibility in its own class.'),
(17, 'multi-client-echo-server', 'Multi-Client Echo Server', 'MEDIUM', 'OOP_GRINDING',
 'Write a TCP server that echoes every line back to its sender and serves several clients at the same time.'),
(18, 'file-transfer-over-tcp', 'File Transfer over TCP', 'MEDIUM', 'OOP_GRINDING',
 'Send a file from a client to a server over a socket, then verify its size and checksum on arrival.'),
(19, 'tcp-chat-room', 'TCP Chat Room', 'HARD', 'OOP_GRINDING',
 'Build a chat server where clients join with a nickname and every message is broadcast to all connected clients.'),
(20, 'event-bus-observer', 'Event Bus with Observers', 'HARD', 'OOP_GRINDING',
 'Implement a type-safe event bus: subscribers register for an event type and are notified when it is published.');

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
