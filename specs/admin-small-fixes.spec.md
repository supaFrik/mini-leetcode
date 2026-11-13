# Feature: Sửa nhỏ — xác nhận khi xoá bài & test judge đang fail

## Overview

Hai việc nhỏ phát hiện khi test toàn bộ chức năng admin (2026-11-06):

1. Nút **Delete** trong `/admin/problems` xoá bài ngay sau một cú click; bài chưa có bài nộp sẽ mất hẳn
   (testcase, rule chấm, lời giải mẫu đi theo).
2. `OopGrindingPipelineRobustnessTest.databasePersistence_CompletedSubmission_DirectEntityInspection`
   fail ổn định trên `main` (kỳ vọng score 100.0, nhận 50.0) — test suite judge không xanh.

## Functional Requirements

### Xác nhận xoá bài
- **FR-SF-001**: When staff clicks Delete on a problem, the UI shall open a confirmation dialog naming the problem title and stating what is removed (test cases, grading rules, reference solution); nothing is deleted until the user confirms.
- **FR-SF-002**: The confirmation dialog shall use the existing `AlertDialog` component (keyboard accessible, focus on Cancel by default, Esc cancels).
- **FR-SF-003**: When the server rejects the delete (409: problem has submissions), the dialog shall close and the toast shall show the server message.
- **FR-SF-004**: The Delete action shall be hidden for INSTRUCTOR (server already restricts `DELETE /api/v1/problems/**` to ADMIN; the UI currently shows the button and fails with 403).

### Test judge đang fail
- **FR-SF-010**: The judge test suite shall pass on `main` with JDK 17 and JDK 21.
- **FR-SF-011**: The root cause shall be identified before changing anything: either the test's expectation is outdated (e.g. weights changed when stdin/stdout test cases joined OOP grading: rules 40 % + test cases 60 %) or the strategy persists a wrong score; the fix goes to whichever is wrong, with the reasoning in the commit message.
- **FR-SF-012**: If the strategy is wrong, a regression test shall pin the correct score for a submission that passes every configured phase.

## Non-Functional Requirements
- No change to grading results of real submissions unless FR-SF-011 shows the strategy is wrong; if it is, list affected submissions (query) so they can be rejudged once `admin-submissions-rejudge` exists.

## Acceptance Criteria

### AC-SF-001: Huỷ xoá
Given ADMIN on `/admin/problems`
When they click Delete on "Two Sum" and then Cancel (or Esc)
Then "Two Sum" is still listed and no DELETE request was sent.

### AC-SF-002: Xác nhận xoá
Given a DRAFT problem without submissions
When ADMIN clicks Delete and confirms
Then the problem disappears and the toast says "Problem deleted."

### AC-SF-003: Giảng viên không thấy nút xoá
Given an INSTRUCTOR on `/admin/problems`
Then no Delete button is shown.

### AC-SF-004: Test judge xanh
Given a clean checkout of `main`
When running `mvnw -pl mini-leetcode-judge test`
Then all tests pass.

## Error Handling

| Error Condition | HTTP | Message |
|-----------------|------|---------|
| Problem has submissions | 409 | Server message, shown in toast |
| Not ADMIN | 403 | Not reachable from UI after FR-SF-004 |

## Implementation TODO

> **Hoàn thành 2026-11-07.**

### Frontend
- [x] Wrap row Delete in `AlertDialog` with title + consequences; Cancel default focus
- [x] Hide Delete when role ≠ ADMIN (read role from session)
- [x] Update e2e: `e2e/admin-problem-delete.spec.ts` (cancel/Esc, confirm, 409, instructor) + live script confirms the dialog

### Backend (judge)
- [x] Reproduce, read the score breakdown the test produces, decide test vs. strategy
- [x] Fix — **the test was outdated, the strategy is right** (see below); judge suite 358/358 on JDK 17 and 21

## Kết quả điều tra FR-SF-011

`score_breakdown` thực tế của test: rule cấu trúc 100 (trọng số 0.5), socket 0 (trọng số 0.5, `CONNECTION_FAILURE`
"Client process exited prematurely"). Test dựng sẵn `execution-report.json` 100/100/100 và một kịch bản socket không có
bước nào chạy trên lớp `Service` (không phải client socket). Từ khi cấu hình chấm chuyển về server, judge không đọc file
đó nữa mà chạy kịch bản thật ⇒ thất bại đúng ⇒ 50 điểm là đúng. Sửa test: chỉ cấu hình rule cấu trúc (bài đạt mọi phần
được cấu hình ⇒ 100 / AC), bỏ report giả, assert trọng số thực. Trường hợp socket thất bại vẫn được test 3.2 phủ.
Không có bài nộp thật nào bị ảnh hưởng (không đổi code chấm).

## Out of Scope
- Soft delete / trash for problems
