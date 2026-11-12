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
| Testcase stdin/stdout | bảng `test_cases` (MinIO), nạp qua API như bài thuật toán | Chạy `main` trong Docker sandbox, so stdout theo `grading_config.output` (mặc định: bỏ khoảng trắng cuối dòng) | 60% |
| Kịch bản socket (tuỳ chọn) | `problems.grading_config` → `"socketScenarios"` | `SocketIntegrationHarness` | 30% (khi có: 30/40/30) |

Bài chỉ có một loại thì loại đó chiếm 100%. Bài **không có** rule, socket lẫn testcase ⇒ bài nộp bị đánh lỗi "no grading configuration", không bao giờ AC.

Rule là danh sách `OopConstraintRule` (xem `mini-leetcode-judge/.../oop_grinding/model`): `targetClassName`, `expectedType` (`CLASS`, `ABSTRACT_CLASS`, `INTERFACE`, `ENUM`, `RECORD`), `expectedSuperclass`, `expectedInterfaces`, `requiredModifiers` / `forbiddenModifiers` (chữ thường), `enforceEncapsulation`, `fields`, `methods` (`name`, `returnType`, `parameterTypes`), `constructors`, `weight`. Ví dụ đầy đủ: `problems/grading/mini-bank-console.json`.

### Cách so stdout và thông báo lỗi (`grading_config.output`, nhãn testcase)

Áp dụng cho cả bài thuật toán lẫn bài OOP. Không có key `output` ⇒ `exact` (hành vi cũ).

```json
{ "output": { "mode": "normalized", "ignoreCase": false }, "rules": [ ... ] }
```

| `mode` | So thế nào |
|--------|-----------|
| `exact` (mặc định) | Từng dòng, bỏ khoảng trắng cuối dòng và dòng trống ở cuối |
| `normalized` | Bỏ khoảng trắng đầu/cuối dòng, gộp khoảng trắng liên tiếp thành một, bỏ mọi dòng trống. Hợp với output căn cột bằng `printf` |

`ignoreCase: true` thì so không phân biệt hoa thường. Khi sai (WA), thông báo trả về cho học viên:

- Testcase **công khai**: dòng lệch đầu tiên, gồm số dòng, dòng kỳ vọng và dòng thực tế (cắt ở 200 ký tự).
- Testcase **ẩn**: chỉ số dòng và nhãn testcase, ví dụ `Wrong answer on test case #3 (hidden · stage 1: two-accounts)`. Không lộ dữ liệu.

Nhãn testcase (cột `test_cases.label`, tối đa 100 ký tự) được gửi qua tham số `label` của `POST/PUT /api/problems/{id}/testcases`. Chỉ sửa nhãn thì được cả khi bài đã PUBLISHED. `seed-two-sum-testcases.ps1` tự đặt nhãn theo tên file: `03-s1-two-accounts-hidden.in` ⇒ `stage 1: two-accounts`; tên khác thì bỏ số thứ tự ở đầu (`02-large.in` ⇒ `large`).

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

## Lộ trình mẫu "OOP với Java" + project Mini Bank 5 stage

Nguồn: `problems/projects/mini-bank/` (`project.json` = plan, stage, testcase; `grading.json` = rule theo `fromStage`; `stages/stage-N.md` = đề; `starters/stage-N/` = file khung). Lời giải tham chiếu dùng chung cho 5 stage là `problems/oop-starters/mini-bank-console/`. Stage N chấm **mọi** testcase và rule của stage 1..N (đặc tả: `specs/study-plan.spec.md`).

```powershell
# 0. Cần API đã chạy một lần (Flyway V10). Nên nạp seed-problems.sql trước: plan dùng 2 bài luyện bank-account-class, shape-area-hierarchy.
python backend\tools\problems\mini_bank_project.py check     # biên dịch bộ khung từng stage

# 1. 5 đề stage + plan oop-voi-java
python backend\tools\problems\mini_bank_project.py sql
Get-Content backend\tools\seed-mini-bank-project.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode

# 2. Testcase cộng dồn: output kỳ vọng sinh trong đúng image sandbox (cần Docker)
python backend\tools\problems\mini_bank_project.py cases

# 3. Nạp testcase từng stage (tài khoản INSTRUCTOR/ADMIN)
1..5 | ForEach-Object { .\backend\tools\seed-two-sum-testcases.ps1 -Slug "mini-bank-stage-$_" -Username <user> -Password <password> }

# 4. Hai bài luyện lẻ (nếu chưa nạp)
python backend\tools\problems\oop.py bank-account-class shape-area-hierarchy
.\backend\tools\seed-two-sum-testcases.ps1 -Slug bank-account-class -Username <user> -Password <password>
.\backend\tools\seed-two-sum-testcases.ps1 -Slug shape-area-hierarchy -Username <user> -Password <password>
```

`cases --local` chạy lời giải bằng `java` trên máy (không cần Docker) để thử nhanh; **đừng nạp kết quả đó lên server** vì mã hoá ký tự có thể khác image của judge. Sửa case / rule xong: chạy lại bước 1–3. Test `MiniBankProjectContentTest` (module judge) kiểm tra rule của mọi stage chấp nhận lời giải tham chiếu.
