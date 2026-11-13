# Feature: Xem bài nộp toàn hệ thống & chấm lại (rejudge)

## Overview

Học viên chỉ xem được bài nộp của mình (`/submissions`); giảng viên/admin không có chỗ xem bài nộp của
người khác, và khi sửa testcase hoặc rule chấm thì các bài đã chấm giữ nguyên kết quả cũ. Tính năng thêm
trang `/admin/submissions` để lọc, xem code và chi tiết chấm của mọi bài nộp, và cho ADMIN **chấm lại** một
bài nộp hoặc toàn bộ bài nộp của một bài tập.

Giá trị: giảng viên theo dõi được lớp, giải đáp thắc mắc "sao em bị WA"; sau khi sửa testcase sai, kết quả
(và bảng xếp hạng contest) được sửa theo mà không phải nhờ học viên nộp lại.

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| D1 | Ai xem | INSTRUCTOR + ADMIN xem **tất cả** bài nộp (hệ thống chưa có khái niệm lớp) |
| D2 | Rejudge | Từng bài nộp + **toàn bộ bài nộp chính thức của một bài tập**; chỉ ADMIN |
| D3 | Bài nộp thử (Run, `quickRun=true`) | Hiện được (lọc riêng), **không** rejudge |
| D4 | Contest đang chạy | **Không** rejudge bài nộp thuộc contest RUNNING (tránh đổi bảng xếp hạng giữa giờ thi) |
| D5 | Kết quả cũ | Ghi lại verdict/điểm trước khi chấm lại để so sánh |

## Functional Requirements

### Xem
- **FR-SB-001**: The `/admin/submissions` page shall list submissions newest first with id, user, problem, contest (if any), language, verdict, score, runtime, memory, submitted time; paged 25/page.
- **FR-SB-002**: The list shall filter by username, problem, contest, verdict, language, status (PENDING/RUNNING/COMPLETED/FAILED), run vs. submit, and date range; filters are kept in the URL.
- **FR-SB-003**: When staff opens a submission, the system shall show its source code (all files for multi-file), per-testcase results including hidden cases, the OOP score breakdown, compile/runtime errors and its rejudge history.
- **FR-SB-004**: While the viewer is INSTRUCTOR, the page shall hide rejudge actions and the rejudge endpoints shall return 403.

### Chấm lại
- **FR-SB-010**: When an ADMIN rejudges a single non-quick-run submission whose contest is not RUNNING, the system shall store the current verdict/score/breakdown in a history row, reset the submission to `PENDING`, and enqueue it on the existing Redis stream via `SubmissionProducer.enqueueSubmission`.
- **FR-SB-011**: When an ADMIN rejudges a problem, the system shall show the count of affected submissions (official, not PENDING/RUNNING, not in a RUNNING contest) and, after confirmation, enqueue them as a batch job; the response returns a job id.
- **FR-SB-012**: The system shall process a batch at most **N submissions in flight** (config `app.rejudge.max-in-flight`, default 10) so normal submissions keep being judged; new student submissions shall not wait behind the whole batch.
- **FR-SB-013**: The batch job page shall show progress (queued / done / changed verdict / failed) and list submissions whose verdict changed (old → new).
- **FR-SB-014**: The system shall reject rejudging a submission that is `PENDING`/`RUNNING`, a quick run, or belongs to a RUNNING contest, with 409 and the reason.
- **FR-SB-015**: Only one batch rejudge per problem may run at a time; a second request returns 409 with the running job id.
- **FR-SB-016**: Contest leaderboards shall reflect rejudged results automatically (they are computed from submissions at read time — no extra work, but must be covered by a test).

## Non-Functional Requirements

### Security
- `GET /api/v1/admin/submissions/**`: `hasAnyRole('INSTRUCTOR','ADMIN')`; `POST …/rejudge`: `hasRole('ADMIN')`.
- Source code downloads go through the API (no direct MinIO URLs to the browser).
- Every rejudge (single or batch) is audited: actor, scope, count, time.

### Performance
- List query ≤ 400 ms p95 with 200 000 submissions; add indexes `(problem_id, created_at)`, `(user_id, created_at)`, `(verdict, created_at)` if missing.
- Batch rejudge of 1 000 submissions must not raise median judge latency of new submissions by more than 2× (enforced by FR-SB-012).

### Reliability
- Batch progress survives an API restart (job state in DB/Redis, not memory); unfinished items are resumed or marked failed.

## Acceptance Criteria

### AC-SB-001: Lọc và xem code
Given 3 students submitted problem "Mini Bank Console"
When an INSTRUCTOR filters by that problem and verdict WRONG_ANSWER and opens one
Then they see the student's files, the failing hidden test case number, and the score breakdown.

### AC-SB-002: Chấm lại cả bài sau khi sửa testcase
Given 20 official submissions of problem P were judged against a wrong expected output
When an ADMIN fixes the test case and runs "Rejudge all (20)"
Then the job page shows progress to 20/20 and lists the submissions whose verdict changed from WA to AC,
And the student's `/submissions` page shows the new verdict.

### AC-SB-003: Không chấm lại khi đang thi
Given submission S belongs to a RUNNING contest
When an ADMIN tries to rejudge S
Then the request is rejected with "Cannot rejudge while the contest is running".

### AC-SB-004: Không chặn bài nộp mới
Given a batch rejudge of 500 submissions is running
When a student submits a new solution
Then it is judged without waiting for the whole batch (within normal latency + at most the in-flight window).

### AC-SB-005: Giảng viên không chấm lại được
Given an INSTRUCTOR views a submission
Then no Rejudge button is shown and `POST /api/v1/admin/submissions/{id}/rejudge` returns 403.

## Error Handling

| Error Condition | HTTP | Message |
|-----------------|------|---------|
| Not staff / INSTRUCTOR rejudging | 403 | "You don't have permission" |
| Submission / problem / job not found | 404 | "Submission not found" |
| Submission pending/running | 409 | "Submission is still being judged" |
| Quick run | 409 | "Test runs cannot be rejudged" |
| Contest running | 409 | "Cannot rejudge while the contest is running" |
| Batch already running for problem | 409 | "A rejudge is already running for this problem" |
| Code object missing in storage | 200 (job item failed) | Item marked failed with "Source code not found" |

## Implementation TODO

### Backend
- [ ] Migration: `submission_judgements(id, submission_id, verdict, score, score_breakdown, pass_test_cases, total_test_cases, judged_at, rejudge_job_id)`; `rejudge_jobs(id, problem_id, actor_id, status, total, done, changed, failed, created_at, finished_at)`; indexes on submissions
- [ ] `AdminSubmissionQueryService` (JPA Specification + paging)
- [ ] `RejudgeService`: single + batch, guards (FR-SB-014/015), history snapshot, throttled enqueue (scheduler feeding up to N in flight)
- [ ] Judge: on completion of a rejudged submission, update job counters (via `rejudge_job_id` on the message)
- [ ] `AdminSubmissionController` `/api/v1/admin/submissions/**`, `/api/v1/admin/rejudge-jobs/**`
- [ ] Audit entries

### Frontend
- [ ] `/admin/submissions` with URL-synced filters and table
- [ ] Submission detail drawer: files viewer, per-case results, breakdown, history
- [ ] "Rejudge" (single) and "Rejudge all" (in problem editor + list) with confirm showing count
- [ ] Job progress panel (polling 2 s)

### Testing
- [ ] Unit: guards matrix, throttling (never > N in flight)
- [ ] Integration: rejudge updates verdict + history; leaderboard reflects change; permissions
- [ ] E2E: fix test case → rejudge all → verdict changes visible to student

## Out of Scope
- Plagiarism re-run (already exists per contest)
- Manual score override / grading comments
- Rejudging quick runs
