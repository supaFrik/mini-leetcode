# Feature: Quản lý người dùng

## Overview

Hiện không có giao diện hay API quản lý tài khoản: nâng quyền giảng viên/admin, khoá tài khoản hay đặt lại
mật khẩu đều phải sửa thẳng bảng `users` bằng SQL. Tính năng thêm trang `/admin/users`: **ADMIN** đổi role,
khoá/mở khoá và reset mật khẩu; **INSTRUCTOR** chỉ xem danh sách.

Giá trị: thao tác quản trị thường ngày không cần quyền truy cập database; các bảo vệ (không tự khoá mình,
luôn còn ít nhất một admin) do hệ thống đảm bảo.

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| D1 | Quyền | ADMIN: xem + sửa. INSTRUCTOR: **chỉ xem**. USER: không thấy |
| D2 | Thao tác | Đổi role (USER/INSTRUCTOR/ADMIN), khoá/mở khoá, reset mật khẩu |
| D3 | Reset mật khẩu | Sinh mật khẩu tạm, **hiển thị một lần** cho admin (chưa có hệ thống email) |
| D4 | Tạo tài khoản hàng loạt | **Không** (ngoài phạm vi) |
| D5 | Hiệu lực | Ngay lập tức — filter JWT đã nạp lại user từ DB ở mỗi request; thêm thu hồi refresh token |

## Functional Requirements

- **FR-US-001**: The `/admin/users` page shall list users with username, email, role, status, created date, number of submissions and last login, with search (username/email), filters (role, status), sorting and paging (20/page).
- **FR-US-002**: When an ADMIN changes a user's role, the system shall persist it and the new role shall apply from that user's next request.
- **FR-US-003**: The system shall reject a role change or block that would leave **zero ACTIVE ADMIN** accounts, with 409.
- **FR-US-004**: The system shall reject an ADMIN changing their own role or blocking themselves, with 409 "You cannot change your own account here".
- **FR-US-005**: When an ADMIN blocks a user, the system shall set status `BLOCKED` and delete all of that user's refresh tokens, so existing sessions end at the next request or token refresh.
- **FR-US-006**: When an ADMIN unblocks a user, the system shall set status `ACTIVE`; the user must log in again.
- **FR-US-007**: When an ADMIN resets a user's password, the system shall generate a random 14-character password (letters, digits, one symbol), store only its BCrypt hash, delete the user's refresh tokens, and return the plaintext **once** in the response; the UI shall show it in a dialog with a copy button and never store it.
- **FR-US-008**: While a user is blocked, when they try to log in, the system shall respond 401 "Account is blocked" (existing behaviour, kept).
- **FR-US-009**: Every role change, block/unblock and password reset shall be written to an audit log (actor, target, action, old → new value, timestamp) and shown on the user's detail panel.
- **FR-US-010**: While the viewer is INSTRUCTOR, the page shall show the list and details read-only, with no action buttons, and the write endpoints shall return 403.
- **FR-US-011**: Destructive actions (block, demote from ADMIN, reset password) shall require a confirmation dialog naming the target user.

## Non-Functional Requirements

### Security
- Endpoints under `/api/v1/admin/users/**`: GET `hasAnyRole('INSTRUCTOR','ADMIN')`; PUT/POST `hasRole('ADMIN')`.
- Responses never include `passwordHash`; temporary passwords appear only in the reset response and are not logged (`log` statements must not include them).
- The "last ADMIN" check runs inside the same transaction with a pessimistic lock on ADMIN rows, so two admins demoting each other concurrently cannot leave zero admins.
- Rate-limit password resets: 20 per admin per hour.

### Performance
- User list ≤ 300 ms p95 with 10 000 users (indexed search on username/email; submission counts via one grouped query).

## Acceptance Criteria

### AC-US-001: Nâng quyền giảng viên
Given ADMIN on `/admin/users`
When they change "gv_nguyen" from USER to INSTRUCTOR and confirm
Then the list shows INSTRUCTOR, the audit log has the entry, and gv_nguyen sees the Admin menu after reloading.

### AC-US-002: Khoá tài khoản có hiệu lực ngay
Given student "sv01" is logged in
When an ADMIN blocks sv01
Then sv01's next API call returns 401 and they are sent to the login page, where login shows "Account is blocked".

### AC-US-003: Không thể mất admin cuối cùng
Given exactly one ACTIVE ADMIN
When anyone tries to demote or block that account
Then the request is rejected with 409 "At least one active admin is required".

### AC-US-004: Reset mật khẩu
Given ADMIN resets sv01's password
Then a dialog shows a 14-character password once; sv01 can log in with it and the old password no longer works; reopening the dialog does not show it again.

### AC-US-005: Giảng viên chỉ xem
Given an INSTRUCTOR opens `/admin/users`
Then they see the list without action buttons, and a direct `PUT /api/v1/admin/users/{id}/role` returns 403.

## Error Handling

| Error Condition | HTTP | Message |
|-----------------|------|---------|
| Not staff / INSTRUCTOR writing | 403 | "You don't have permission" |
| User not found | 404 | "User not found" |
| Change own role / block self | 409 | "You cannot change your own account here" |
| Would leave no active admin | 409 | "At least one active admin is required" |
| Invalid role/status value | 400 | "Unknown role" |
| Too many resets | 429 | "Too many password resets, try again later" |

## Implementation TODO

### Backend
- [ ] Migration: `user_audit_log(id, actor_id, target_id, action, old_value, new_value, created_at)`; `users.last_login_at`
- [ ] Set `last_login_at` on successful login
- [ ] `UserAdminService`: list (spec + paging), changeRole, setStatus, resetPassword, audit
- [ ] `UserAdminController` `/api/v1/admin/users/**` (+ `@PreAuthorize`)
- [ ] Delete refresh tokens on block/reset (`RefreshTokenRepository.deleteByUserId`)
- [ ] Last-admin guard with `SELECT … FOR UPDATE` on ADMIN rows
- [ ] Password-reset rate limit (Redis counter, same pattern as console quota)

### Frontend
- [ ] `/admin/users` page (table, search, filters, paging) + admin nav link
- [ ] Detail drawer: info, audit log, actions (ADMIN only)
- [ ] Confirm dialogs; one-time password dialog with copy button
- [ ] Handle 401 after own session revoked (existing redirect)

### Testing
- [ ] Unit: last-admin guard, self-change guard, password generator strength
- [ ] Integration: permission matrix, block revokes refresh token, reset makes old password fail
- [ ] E2E: promote → login as instructor sees Admin menu; block → logged-out

## Out of Scope
- Creating users / bulk CSV import
- Email-based password reset, email verification
- Deleting users (submissions must keep their author)
- Classes/groups of students
