# backend/tools (chỉ dùng cho dev)

Testcase (`<slug>/cases/`) không được commit. Sinh lại từ generator (random có seed cố định nên ra đúng các file cũ), rồi nạp qua API.

`seed-problems.sql` cũng không được commit. Muốn sửa bài thì sửa `problems/problems.json` (metadata, chủ đề) và `problems/statements/<slug>.md` (đề), rồi sinh lại.

```powershell
# 1. Bài, đề và chủ đề: sinh seed-problems.sql rồi nạp vào MySQL
python backend\tools\problems\seed_sql.py
Get-Content backend\tools\seed-problems.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode

# 2. Sinh testcase vào backend\tools\<slug>\cases\
python backend\tools\two-sum\gen.py
python backend\tools\problems\dsa.py
python backend\tools\problems\oop.py

# 3. Nạp testcase của một bài (cần tài khoản INSTRUCTOR hoặc ADMIN)
.\backend\tools\seed-two-sum-testcases.ps1 -Slug coin-change -Username <user> -Password <password>
```

Mỗi generator nhận thêm danh sách slug để chỉ sinh vài bài, ví dụ `python backend\tools\problems\dsa.py coin-change`.

## Chấm bài OOP (`problem_type = OOP_GRINDING`)

Judge **không** đọc bất kỳ cấu hình chấm nào trong bài nộp: `harness-config.json`, `rules.json`, `socket-scenarios.json`, lớp `*Test.java` của học viên đều bị bỏ qua. Mỗi bài OOP cần cấu hình phía server:

| Thành phần | Lưu ở đâu | Chấm thế nào | Trọng số |
|-----------|-----------|--------------|---------|
| Rule cấu trúc OOP | `problems.grading_config` → `"rules"` | Reflection trên `.class` đã biên dịch (không chạy code học viên) | 40% |
| Testcase stdin/stdout | bảng `test_cases` (MinIO), nạp qua API như bài thuật toán | Chạy `main` trong Docker sandbox, so stdout (bỏ khoảng trắng cuối dòng) | 60% |
| Kịch bản socket (tuỳ chọn) | `problems.grading_config` → `"socketScenarios"` | `SocketIntegrationHarness` | 30% (khi có: 30/40/30) |

Bài chỉ có một loại thì loại đó chiếm 100%. Bài **không có** rule, socket lẫn testcase ⇒ bài nộp bị đánh lỗi "no grading configuration", không bao giờ AC.

Rule là danh sách `OopConstraintRule` (xem `mini-leetcode-judge/.../oop_grinding/model`): `targetClassName`, `expectedType` (`CLASS`, `ABSTRACT_CLASS`, `INTERFACE`, `ENUM`, `RECORD`), `expectedSuperclass`, `expectedInterfaces`, `requiredModifiers` / `forbiddenModifiers` (chữ thường), `enforceEncapsulation`, `fields`, `methods` (`name`, `returnType`, `parameterTypes`), `constructors`, `weight`. Ví dụ đầy đủ: `problems/grading/mini-bank-console.json`.

Ví dụ bài Mini Bank Console:

```powershell
# 1. Bài + đề + starter + grading_config (rule)
python backend\tools\problems\seed_oop_console.py
Get-Content backend\tools\seed-mini-bank-console.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode

# 2. Testcase: output kỳ vọng sinh bằng lời giải mẫu chạy trong đúng image sandbox (cần Docker)
python backend\tools\problems\oop_console_cases.py

# 3. Nạp testcase (tài khoản INSTRUCTOR/ADMIN)
.\backend\tools\seed-two-sum-testcases.ps1 -Slug mini-bank-console -Username <user> -Password <password>
```
