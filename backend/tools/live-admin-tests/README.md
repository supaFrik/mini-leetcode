# Live admin tests

Chạy trên stack Docker đang chạy (API :8080, web :3100, judge có Docker). Mỗi script tự tạo bài/user tạm và tự xoá bài khi xong.

```powershell
$env:ADMIN_PW = "<mật khẩu tài khoản devadmin>"
# 1. API: mọi endpoint admin/instructor (71 kiểm tra)
node backend/tools/live-admin-tests/admin-api-test.mjs
# 2. UI: trình duyệt thật đi hết luồng soạn bài (cần node_modules của frontend)
$env:FRONTEND_DIR = (Resolve-Path frontend).Path; $env:SHOTS = "$env:TEMP\admin-shots"; mkdir $env:SHOTS -Force
node backend/tools/live-admin-tests/admin-ui-test.mjs
```

Script API tạo một user `smoke_stu_*` để kiểm tra phân quyền; xoá bằng SQL nếu cần:
`delete from refresh_tokens where user_id in (select id from users where username like 'smoke_stu_%'); delete from users where username like 'smoke_stu_%';`
