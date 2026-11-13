# Feature: Quản lý kỳ thi (Contest) trên giao diện admin

## Overview

Hiện kỳ thi chỉ tạo được bằng SQL (`backend/tools/seed-contests.sql`); API chỉ có đọc, đăng ký, nộp bài,
báo cáo giám sát và mở khoá thí sinh. Tính năng này cho **giảng viên và admin** tạo, sửa, xoá kỳ thi,
chọn bài + nhãn (A, B, C…), cấu hình chấm và giám sát, và quản lý danh sách thí sinh ngay trên
`/admin/contests`.

Giá trị: giảng viên tự tổ chức một buổi thi trong vài phút, không cần người biết SQL; luật "đang thi thì
không đổi đề" được hệ thống đảm bảo thay vì dựa vào sự cẩn thận.

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| D1 | Người dùng | INSTRUCTOR + ADMIN tạo/sửa contest; **xoá** chỉ ADMIN |
| D2 | Sửa khi đang thi (RUNNING) | **Chỉ gia hạn giờ kết thúc**; khoá danh sách bài, cách tính điểm, giờ bắt đầu, số vi phạm cho phép |
| D3 | Sau khi kết thúc (COMPLETED) | Khoá hoàn toàn (chỉ xem, xem báo cáo) |
| D4 | Vào thi | Giữ **tự đăng ký**; thêm tuỳ chọn `registrationMode = OPEN | INVITE_ONLY`; giảng viên dán danh sách username/email để thêm thí sinh |
| D5 | Bài trong contest | Chỉ bài **PUBLISHED** (như seed hiện tại); mỗi bài có nhãn (A, B…) và thứ tự |
| D6 | Trạng thái | Giữ cách tính hiện có (`Contest.resolveStatus()` theo giờ); không thêm nút "bắt đầu/dừng" thủ công |
| D7 | Xoá | Chỉ khi contest còn SCHEDULED **và** chưa có bài nộp nào |

## Functional Requirements

### Tạo & sửa

- **FR-CT-001**: When an INSTRUCTOR or ADMIN submits the create form with title, start time, end time, scoring policy (`ACM_ICPC` | `PARTIAL_IOI`), `maxViolationsAllowed` and registration mode, the system shall create the contest in `SCHEDULED` state, generate a unique slug from the title, and record the creator.
- **FR-CT-002**: The system shall reject a contest whose end time is not strictly after its start time, or whose start time is in the past at creation.
- **FR-CT-003**: While a contest is `SCHEDULED`, when staff edits it, the system shall allow changing every field, including problems and registration mode.
- **FR-CT-004**: While a contest is `RUNNING`, when staff edits it, the system shall accept only a later end time and shall reject any other change with 409.
- **FR-CT-005**: While a contest is `COMPLETED`, the system shall reject every edit with 409.
- **FR-CT-006**: The system shall store times in UTC and the UI shall show and accept them in the viewer's local time zone, displaying the zone next to the field.

### Bài thi

- **FR-CT-010**: When staff adds problems to a `SCHEDULED` contest, the system shall accept only `PUBLISHED` problems and assign labels A, B, C… in order, editable by staff (1–3 characters, unique within the contest).
- **FR-CT-011**: The system shall allow reordering and removing problems only while the contest is `SCHEDULED`.
- **FR-CT-012**: When a problem used by a non-completed contest is moved out of `PUBLISHED`, the system shall reject the transition with 409 naming the contest.
- **FR-CT-013**: The system shall refuse to save a contest with zero problems once its start time has passed (a contest must have at least one problem to start).

### Thí sinh

- **FR-CT-020**: Where `registrationMode = OPEN`, the system shall keep the existing self-registration flow.
- **FR-CT-021**: Where `registrationMode = INVITE_ONLY`, when a user who is not on the list tries to register, the system shall reject with 403 "This contest is invite-only".
- **FR-CT-022**: When staff pastes a list of usernames or emails (one per line or comma-separated, up to 500), the system shall register each existing, ACTIVE user and return per-line results: `added`, `already registered`, `not found`, `blocked`.
- **FR-CT-023**: While a contest is `SCHEDULED` or `RUNNING`, when staff removes a participant who has no submissions in the contest, the system shall delete the registration; if the participant has submissions, the system shall reject with 409.
- **FR-CT-024**: The participant list shall show username, status (REGISTERED/SUBMITTED/LOCKED/DISQUALIFIED), violation count and score, and link to the existing proctoring report and unlock action.

### Xoá & danh sách

- **FR-CT-030**: When an ADMIN deletes a contest that is `SCHEDULED` and has no submissions, the system shall delete it with its problem links and registrations after an explicit confirmation in the UI.
- **FR-CT-031**: The system shall reject deleting a contest that is `RUNNING`, `COMPLETED`, or has submissions, with 409.
- **FR-CT-032**: The `/admin/contests` page shall list contests with title, status, start/end, problem count, participant count, and filters by status and search by title.

## Non-Functional Requirements

### Security
- `POST/PUT /api/v1/admin/contests/**` and participant management: `hasAnyRole('INSTRUCTOR','ADMIN')`; `DELETE`: `hasRole('ADMIN')`. Enforced both in `SecurityConfig` (`/api/v1/admin/**` matcher already exists) and `@PreAuthorize`.
- Staff contest endpoints never expose hidden test cases or other participants' code.
- Server is the source of truth for status: all locking rules (FR-CT-004/005/011) are checked server-side against `resolveStatus()` at request time, not against what the UI last saw.

### Performance
- Contest list ≤ 300 ms p95 for 500 contests (single query with counts, no N+1).
- Bulk participant add of 500 lines ≤ 2 s (batch lookup by username/email).

### Reliability
- Editing a contest is one transaction; concurrent edits use optimistic locking (`@Version` on `Contest`) and return 409 "Contest was changed by someone else, reload".

## Acceptance Criteria

### AC-CT-001: Tạo kỳ thi
Given an INSTRUCTOR on `/admin/contests`
When they create "Giữa kỳ OOP" from tomorrow 08:00 to 10:00, ACM_ICPC, max violations 3, and add 3 published problems
Then the contest appears as Scheduled with labels A, B, C and is visible in the student contest list.

### AC-CT-002: Đang thi chỉ gia hạn được
Given a RUNNING contest ending at 10:00
When staff changes the end time to 10:30
Then it is saved and the countdown of participants reflects 10:30 on their next refresh;
And when staff tries to remove problem B, the request is rejected with "Only the end time can be changed while the contest is running".

### AC-CT-003: Không cho giờ kết thúc trước giờ bắt đầu
Given the create form
When end time ≤ start time
Then the form shows an inline error and the API returns 400 if called directly.

### AC-CT-004: Thi theo danh sách
Given an INVITE_ONLY contest and staff pasted "sv01, sv02, khongtontai"
Then the result shows sv01 added, sv02 added, khongtontai not found;
And when sv03 tries to register, they see "This contest is invite-only".

### AC-CT-005: Khoá bài đang dùng trong kỳ thi
Given problem P is in a SCHEDULED contest
When staff archives P
Then the transition is rejected with 409 naming the contest.

### AC-CT-006: Xoá
Given a SCHEDULED contest without submissions
When an ADMIN confirms delete
Then the contest disappears; and an INSTRUCTOR does not see the delete button.

## Error Handling

| Error Condition | HTTP | Message |
|-----------------|------|---------|
| End ≤ start, start in the past, invalid label | 400 | Field-specific message |
| Not staff | 403 | "You don't have permission" |
| Register to invite-only contest | 403 | "This contest is invite-only" |
| Contest not found | 404 | "Contest not found" |
| Change other than end time while RUNNING; any change when COMPLETED | 409 | "Only the end time can be changed while the contest is running" / "Contest has ended" |
| Archive a problem used by a contest | 409 | "Problem is used by contest '<title>'" |
| Delete running/completed/with submissions | 409 | "Only scheduled contests without submissions can be deleted" |
| Concurrent edit | 409 | "Contest was changed by someone else, reload" |

## Implementation TODO

### Backend
- [x] Migration V9: `contests.registration_mode VARCHAR(20) NOT NULL DEFAULT 'OPEN'`, `contests.version BIGINT NOT NULL DEFAULT 0`
- [x] DTOs `CreateContestRequest`, `UpdateContestRequest`, `ContestProblemInput(label, problemId, orderIndex)`, `BulkParticipantsRequest/Result`
- [x] `ContestAdminService`: create, update (status-aware locking), set problems, delete, add/remove participants
- [x] `ContestAdminController` at `/api/v1/admin/contests/**`
- [x] Hook into `ProblemService.transition` to block leaving PUBLISHED while used by a non-completed contest
- [x] Enforce `INVITE_ONLY` in `ContestService.register`
- [x] Batch-load users by username/email for bulk add

### Frontend
- [x] `/admin/contests` list page (filters, counts) + nav entry in admin area
- [x] Contest editor drawer: Details / Problems (picker of PUBLISHED problems, labels, drag reorder) / Participants (paste list, results table, remove)
- [x] Disable fields per status with an explanatory notice (same pattern as Published lock in problem editor)
- [x] Local-time datetime inputs with zone label
- [x] Delete with confirmation dialog (ADMIN only)

### Testing
- [x] Unit: status-based locking matrix, label validation, invite-only
- [x] Integration: CRUD + permission matrix (USER/INSTRUCTOR/ADMIN/anonymous), optimistic lock conflict
- [x] E2E: create → add problems → invite → student registers/blocked

## Out of Scope
- Starting/stopping a contest manually, pausing
- Per-participant extra time
- Contest cloning, templates
- Notifications/emails to participants

## Ghi chú triển khai

- **Trạng thái tính theo giờ.** Trước đây không có gì chuyển cột `status` sang `RUNNING` (seed ghi thẳng).
  `Contest.resolveStatus()` giờ: cột `COMPLETED` → COMPLETED; quá `endTime` → COMPLETED; cột `SCHEDULED` mà đã tới
  `startTime` → RUNNING. Kỳ thi tạo trên admin vì vậy tự bắt đầu/kết thúc đúng giờ, không cần job.
- **API:** `GET/POST /api/v1/admin/contests`, `GET/PUT/DELETE /api/v1/admin/contests/{id}`,
  `GET/POST /api/v1/admin/contests/{id}/participants`, `DELETE /api/v1/admin/contests/{id}/participants/{userId}`.
  `PUT` nhận cả form; `problems: null` = giữ nguyên; `version` lệch → 409. Mọi lần lưu đều tăng `version`
  (`OPTIMISTIC_FORCE_INCREMENT`), kể cả khi chỉ đổi danh sách bài.
- **So sánh giờ theo phút** khi xét "đã sửa giờ bắt đầu" (ô nhập chỉ tới phút); UI gửi lại nguyên văn giờ bắt đầu khi đang thi.
- **FR-CT-013:** giờ bắt đầu luôn ở tương lai khi tạo/sửa lúc SCHEDULED, nên lần "lưu sau giờ bắt đầu" duy nhất là gia hạn
  khi đang thi — bị từ chối nếu kỳ thi không có bài. Trình soạn nhắc "cần ít nhất một bài trước giờ bắt đầu".
- **Bài trong kỳ thi chưa kết thúc** cũng không xoá cứng được (ngoài việc không rời PUBLISHED).
- **Sắp xếp bài** bằng nút lên/xuống (bàn phím dùng được) thay cho kéo-thả.
- **Thí sinh LOCKED** = trạng thái `DISQUALIFIED` hiện có; mở khoá vẫn ở báo cáo giám sát `/exam/{id}`.
- Trang admin không có bản mock (`NEXT_PUBLIC_USE_MOCK`); e2e chặn request bằng `page.route`.
