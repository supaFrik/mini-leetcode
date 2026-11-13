# Lộ trình tính năng quản trị (chốt 2026-11-07)

Thứ tự làm đã thống nhất. Người dùng chính: giảng viên (INSTRUCTOR) + admin; thao tác nhạy cảm (đổi role, xoá, rejudge) chỉ ADMIN.

| # | Spec | Việc chính | Ước lượng |
|---|------|-----------|-----------|
| 1 | [admin-small-fixes](admin-small-fixes.spec.md) | Xác nhận khi xoá bài; ẩn Delete với INSTRUCTOR; sửa test judge đang fail | Nhỏ |
| 2 | [admin-contest-management](admin-contest-management.spec.md) | Tạo/sửa/xoá contest, chọn bài + nhãn, invite-only, thêm thí sinh theo danh sách; đang thi chỉ gia hạn giờ kết thúc | Lớn |
| 3 | [admin-user-management](admin-user-management.spec.md) | Đổi role, khoá/mở khoá, reset mật khẩu (hiện 1 lần), audit log; giảng viên chỉ xem | Vừa |
| 4 | [admin-testcase-bulk-import](admin-testcase-bulk-import.spec.md) | Import ZIP `1.in/1.out`, Append hoặc Replace all (một giao dịch), chống zip-bomb | Vừa |
| 5 | [admin-submissions-rejudge](admin-submissions-rejudge.spec.md) | Xem mọi bài nộp; rejudge từng bài + cả bài tập (≤ 10 đồng thời), lưu lịch sử verdict | Lớn |

Quyết định chung: mọi API mới nằm dưới `/api/v1/admin/**` (đã có matcher staff-only trong `SecurityConfig`) và có thêm `@PreAuthorize`; mọi thao tác phá huỷ có hộp xác nhận nêu rõ đối tượng.
