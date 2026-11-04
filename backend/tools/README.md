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
