# Feature: OOP Interactive Console & Real Runtime/Memory Metrics

> **Phần console (`FR-CONS-*`, Q1/Q2/Q6/Q9–Q12/Q18) đã bị thay thế bởi `live-terminal-console.spec.md`.** Phần `FR-METRIC-*` vẫn hiệu lực.

## Overview

Bổ sung tính năng **console stdin/stdout tương tác** cho các bài `ProblemType = OOP_GRINDING`, đồng thời **sửa lỗi Runtime và Peak memory không hiển thị** trên đúng dạng bài này.

Hai vấn đề gốc đã xác minh trong codebase:

1. `OopGrindingJudgeStrategy.recordExecutionResults()` gọi `updateSubmissionVerdict(..., 0, 0, ...)` → cột `execution_time_ms` và `memory_used_kb` bị ghi cứng `0` ⇒ UI hiển thị `Runtime: 0 ms | Peak memory: —`.
2. `OopDockerRunner` chạy image `mini-leetcode-judge-runner:java17` (thực chất là `eclipse-temurin:17-jdk`, **không chứa** `/opt/oop-harness.jar`) ⇒ luôn rơi về `runInProcessHarness()`, code học viên chạy trong JVM của judge worker ⇒ **không đo được** `cgroup memory.peak` và không có wall-clock thật.

Giá trị cho người dùng: học viên OOP được gõ thử biểu thức Java trực tiếp trên lớp của chính họ (`new Circle("c", 2).getArea()`) trong một sandbox Docker cách ly, và thấy **số liệu Runtime / Peak memory thật** thay vì `0 ms` / `—`.

---

## Scope & Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| Q1 | Kênh truyền tải console | HTTP request/response từng lệnh (`POST /api/v1/problems/{id}/console/execute`) |
| Q2 | Vị trí UI | Tab "Console" riêng trong khung dưới của workspace, cạnh tab Testcase / Test Result / Submissions |
| Q3 | Bài nào được bật | Mọi bài có `problem_type = 'OOP_GRINDING'` (không thêm cột DB) |
| Q4 | Đo Runtime & Peak memory | Ở cấp container: wall-clock (`docker inspect` StartedAt/FinishedAt) + `cgroup memory.peak`; ghi vào `execution_time_ms` / `memory_used_kb` |
| Q5 | Nơi thực thi code học viên | API gọi judge worker qua HTTP nội bộ (`mini-leetcode-judge` bật `spring-boot-starter-web`) |
| Q6 | Entry point console | Tự phát hiện `public static void main(String[])`; nếu không có → sinh harness REPL biểu thức (Q18) |
| Q7 | Đo số liệu thật cho bài OOP | Chạy code học viên trong **1 container ephemeral riêng** để đo; phần chấm điểm OOP vẫn giữ in-process |
| Q8 | Ảnh hưởng tới điểm | **Không** — console không sinh `submissions` row, không tính điểm, không vào thống kê/xếp hạng |
| Q9 | Lưu transcript | Redis TTL (~1h), key theo `userId + problemId` — không cần migration DB |
| Q10 | Giới hạn an toàn | timeout 3s/lệnh (+2s khởi động JVM), output ≤ 64KB/lệnh, memory 256MB, ≤ 50 lệnh/session |
| Q11 | Trạng thái JVM giữa lệnh | Không giữ — mỗi lệnh = 1 container mới; **cache class đã compile** theo hash mã nguồn |
| Q12 | Hợp đồng API | Public tổng hợp `POST /api/v1/problems/{id}/console/execute`; internal `POST /internal/console/execute` |
| Q13 | Xác thực API → worker | Header `X-Internal-Token` + chỉ expose trong docker network nội bộ (không publish port) |
| Q14 | Hiển thị số đo | Sửa backend ghi số thật vào 2 cột ⇒ UI hiện tại tự đúng, không sửa component |
| Q15 | 2 bài OOP trong DB (0 testcase) | Seed testcase làm **mẫu stdin cho console** (không tính điểm) |
| Q16 | E2E | Mở rộng `e2e/workspace-oop-multifile.spec.ts` + thêm `e2e/console-oop.spec.ts` (mock API) |
| Q17 | Ngữ nghĩa testcase OOP | Mẫu stdin cho console + đầu vào đo runtime/memory; **không** tính vào điểm |
| Q18 | Console cho bài OOP không có `main` | REPL biểu thức: sinh harness reflection để gõ biểu thức Java trực tiếp |
| Q19 | Khi không có Docker | **Không fallback in-process** ⇒ trả `503`, tuyệt đối không chạy code học viên trong JVM worker |
| Q20 | Console trong exam mode | Ẩn / khoá console trong kỳ thi |
| Q21 | Ai xem transcript | Chủ sở hữu + staff (điều tra gian lận) |

---

## Functional Requirements

### FR-CONS-001: Bật console cho bài OOP
While người dùng đang xem một bài có `problem_type = 'OOP_GRINDING'`, the system shall hiển thị thêm tab "Console" trong khung dưới của workspace.

### FR-CONS-002: Ẩn console trong kỳ thi
While người dùng đang ở chế độ thi (`examMode`) hoặc phiên đã bị khoá (`locked`), the system shall không hiển thị tab "Console".

### FR-CONS-003: Gửi lệnh console
While phiên console đang mở, when người dùng gửi một biểu thức hoặc chuỗi vào, the system shall POST payload gồm `files` (mã nguồn hiện tại trong editor) và `input` (nội dung ô nhập) tới `POST /api/v1/problems/{problemId}/console/execute`.

### FR-CONS-004: Biên dịch mã nguồn trước khi chạy
While mã nguồn gửi lên chưa được biên dịch thành công, the system shall biên dịch trong sandbox trước khi thực thi, và khi biên dịch thất bại thì trả về `compileErrors` kèm vị trí lỗi mà **không** thực thi.

### FR-CONS-005: Cache lớp đã biên dịch
While mã nguồn không đổi kể từ lần biên dịch gần nhất, the system shall tái dùng tập `.class` đã biên dịch thay vì biên dịch lại.

### FR-CONS-006: Phát hiện entry point
While các lớp đã biên dịch chứa một lớp có `public static void main(String[])`, the system shall chạy lớp đó với `input` làm `stdin`.
While không lớp nào có `main`, the system shall sinh harness REPL biểu thức để đánh giá biểu thức người dùng gõ.

### FR-CONS-007: Thực thi trong sandbox cách ly
While mã nguồn đã biên dịch, the system shall chạy nó trong container Docker ephemeral với các cờ sandbox `--network none`, `--memory 256m`, `--cpus 1.0`, `--pids-limit 64`, `--read-only`, `--cap-drop ALL`, `--security-opt no-new-privileges:true`, `--user 1001:1001`.

### FR-CONS-008: Không fallback in-process
While Docker không khả dụng, the system shall trả HTTP `503` với thông báo "Docker sandbox is unavailable" và **không** thực thi mã nguồn học viên trong JVM của judge worker.

### FR-CONS-009: Trả kết quả lệnh
While một lệnh console đã được thực thi, the system shall trả về `stdout`, `stderr`, `exitCode`, `runtimeMs`, `peakMemoryKb`, `compileErrors` (nếu có) và cờ `truncated` khi output bị cắt cụt.

### FR-CONS-010: Giới hạn an toàn
While mỗi lệnh console được thực thi, the system shall áp dụng: timeout 3.000ms (+2.000ms khởi động JVM), giới hạn 64KB cho stdout và 64KB cho stderr, giới hạn 256MB bộ nhớ, và tối đa 50 lệnh cho mỗi phiên.

### FR-CONS-011: Ghi transcript
While một lệnh console hoàn tất, the system shall lưu vào Redis key `console:transcript:{userId}:{problemId}` một transcript gồm các bản ghi `{ input, stdout, stderr, exitCode, runtimeMs, peakMemoryKb, at }` với TTL 1 giờ.

### FR-CONS-012: Đọc transcript
While người dùng mở lại trang bài hoặc F5, when họ gọi `GET /api/v1/problems/{problemId}/console/transcript`, the system shall trả transcript đang lưu trong Redis.

### FR-CONS-013: Xóa transcript
While người dùng nhấn nút "Clear", when `DELETE /api/v1/problems/{problemId}/console/transcript` được gọi, the system shall xóa transcript của họ và trả về `204 No Content`.

### FR-CONS-014: Phân quyền transcript
While người dùng không phải chủ sở hữu của transcript và không có vai trò `INSTRUCTOR`/`ADMIN`, the system shall trả `403 Forbidden`.

### FR-CONS-015: Xác thực nội bộ
While judge worker nhận request vào `POST /internal/console/execute`, the system shall từ chối trả `401` nếu header `X-Internal-Token` thiếu hoặc không khớp cấu hình.

### FR-METRIC-001: Ghi Runtime thật cho bài OOP
While một bài nộp `OOP_GRINDING` đã được thực thi trong container, the system shall ghi wall-clock thực tế (ms) vào `submissions.execution_time_ms` thay vì giá trị `0`.

### FR-METRIC-002: Ghi Peak memory thật cho bài OOP
While một bài nộp `OOP_GRINDING` đã được thực thi trong container, the system shall ghi peak memory của cgroup (KB) vào `submissions.memory_used_kb` thay vì giá trị `0`.

### FR-METRIC-003: Fallback khi không đo được
While container không phát ra sentinel `__MEM_PEAK_KB__` hoặc không chạy được, the system shall ghi `null` cho số đo tương ứng thay vì ghi `0` (vì `0` bị hiển thị là "0 KB" và gây hiểu nhầm).

### FR-METRIC-004: Đưa số đo vào breakdown
While một bài nộp OOP hoàn tất, the system shall ghi kèm `runtimeMs` và `peakMemoryKb` vào JSON `score_breakdown` để phân tích thêm.

### FR-METRIC-005: Hiển thị ở UI
While kết quả bài nộp OOP được hiển thị, the system shall hiển thị `Runtime: {executionTimeMs} ms` và `Peak memory: {memoryUsedKb}` bằng số đo thật (dùng lại `problem-result.tsx` hiện có, không nhánh riêng).

---

## Non-Functional Requirements

### Performance
- Độ trễ một lệnh console: ≤ 5.000ms p95 (gồm khởi động JVM trong container).
- Compile cache hit: ≤ 3.000ms p95 cho mỗi lệnh.
- Transcript đọc/ghi: ≤ 100ms p95 (Redis).
- Số đo Runtime/Peak memory của bài nộp OOP: độ trễ chấm tăng ≤ 1.500ms so với hiện tại (thêm 1 container ephemeral).

### Security
- Mã nguồn học viên **chỉ** được thực thi trong container Docker, không bao giờ trong JVM của API hay judge worker.
- Container: `--network none`, `--memory 256m`, `--cpus 1.0`, `--pids-limit 64`, `--read-only`, `--cap-drop ALL`, `--security-opt no-new-privileges:true`, `--user 1001:1001`, `--tmpfs /tmp:rw,noexec,nosuid,size=64m`.
- Endpoint nội bộ yêu cầu `X-Internal-Token`; judge worker **không** publish port ra host (chỉ trong docker network nội bộ).
- Mọi đường dẫn file nguồn đi qua `SourcePathValidator` (chống Zip Slip, path traversal, ký tự shell).
- Tổng dung lượng mã nguồn gửi lên ≤ 20MB, ≤ 100 file.
- Transcript trong Redis đã giới hạn TTL 1h và tối đa 50 bản ghi ⇒ không lưu dữ liệu vô hạn.

### Scalability
- Judge worker chịu được ≥ 20 lệnh console đồng thời (mỗi lệnh 1 container, giới hạn bởi `--cpus` và `--memory` của service).
- Transcript không tăng tải MySQL.

### Observability
- Log mỗi lệnh console: `userId`, `problemId`, `runtimeMs`, `peakMemoryKb`, `exitCode`.
- Log cảnh báo khi rơi vào nhánh không đo được số liệu.

---

## Acceptance Criteria

### AC-001: Console xuất hiện trên bài OOP
Given một bài có `problem_type = 'OOP_GRINDING'`
When người dùng mở trang bài ở chế độ student
Then khung dưới workspace hiển thị thêm tab "Console"
And không hiển thị tab "Console" trên bài `ALGORITHMIC`.

### AC-002: Console bị ẩn trong kỳ thi
Given người dùng đang ở chế độ thi (`examMode`)
When trang bài hiển thị
Then tab "Console" không xuất hiện
And gọi `POST /api/v1/problems/{id}/console/execute` từ client bị từ chối với `403`.

### AC-003: Chạy biểu thức OOP thành công
Given học viên đã viết `Shape.java` (abstract) + `Circle.java` với `getArea()` trả `Math.PI * radius * radius`
And mở tab Console và gõ `new Circle("c", 2).getArea()`
When học viên nhấn Send
Then API trả `200` với `stdout` chứa giá trị xấp xỉ `12.566370614359172`
And `exitCode` bằng `0`
And `runtimeMs` > 0
And `peakMemoryKb` > 0
And vùng log trong tab Console hiển thị giá trị vừa nhận.

### AC-004: Lỗi biên dịch được báo rõ
Given học viên viết `Circle.java` thiếu dấu `;`
When học viên gõ một biểu thức bất kỳ trong Console
Then API trả `200` với `compileErrors` khác null, nội dung có `Circle.java` và số dòng
And `stdout` rỗng
And tab Console hiển thị lỗi biên dịch, không hiển thị kết quả chạy.

### AC-005: Container không khả dụng ⇒ 503
Given Docker daemon không khả dụng
When người dùng gửi một lệnh console
Then API trả `503` với thông báo chứa "Docker sandbox is unavailable"
And **không** có bất kỳ dấu hiệu nào cho thấy mã nguồn đã chạy trong JVM của worker (log không ghi "in-process").

### AC-006: Timeout lệnh console
Given một lệnh console gây treo vô hạn (`while(true){}`)
When người dùng gửi lệnh đó
Then API trả trong ≤ 6.000ms với `exitCode` khác `0` và `stderr` chứa thông báo timeout
And container bị `docker kill` và không còn tồn tại sau khi hoàn tất.

### AC-007: Giới hạn output
Given một lệnh in ra > 64KB dữ liệu
When người dùng gửi lệnh đó
Then `truncated` bằng `true`
And `stdout` không vượt quá 64KB.

### AC-008: Giới hạn số lệnh trong phiên
Given một phiên đã thực thi 50 lệnh console
When người dùng gửi lệnh thứ 51
Then API trả `429 Too Many Requests` với thông báo giới hạn phiên.

### FR-METRIC AC-009: Runtime và Peak memory hiển thị đúng cho bài OOP
Given một bài nộp `OOP_GRINDING` biên dịch thành công và chạy được trong container
When người dùng xem tab "Test Result"
Then banner hiển thị `Runtime: {N} ms` với `N` > 0 (không phải `0`)
And banner hiển thị `Peak memory: {M} MB` hoặc `{M} KB` với `M` > 0 (không phải `—`)
And cột `submissions.execution_time_ms` trong DB lưu đúng giá trị đó
And cột `submissions.memory_used_kb` trong DB lưu đúng giá trị đó.

### AC-010: Compile lỗi thì không ghi số đo giả
Given một bài nộp OOP lỗi biên dịch (fail-fast, verdict `CE`)
When người dùng xem kết quả
Then `execution_time_ms` và `memory_used_kb` là `NULL` (không phải `0`)
And UI hiển thị `Runtime: — ms` và `Peak memory: —`.

### AC-011: Transcript sống qua reload
Given người dùng đã chạy 3 lệnh console
When họ reload trang
Then tab Console hiển thị lại 3 bản ghi cũ.

### AC-012: Transcript bị giới hạn quyền truy cập
Given học viên A đã tạo transcript cho bài X
When học viên B gọi `GET /api/v1/problems/X/console/transcript`
Then API trả `403 Forbidden`
And học viên A gọi cùng endpoint thì nhận `200` với transcript của họ.

### AC-013: Nội bộ yêu cầu token
Given một request tới `POST /internal/console/execute` không có header `X-Internal-Token`
When judge worker nhận request
Then trả `401 Unauthorized`
And không thực thi bất kỳ mã nguồn nào.

### AC-014: Console không ảnh hưởng điểm và thống kê
Given học viên chạy 10 lệnh console rồi nộp bài OOP
When kết quả bài nộp được lưu
Then `submissions` chỉ có đúng 1 row cho lần nộp đó (không có row cho console)
And điểm bài nộp không thay đổi so với khi không dùng console
And `/api/v1/submissions/my/summary` không đếm các lần chạy console.

### AC-015: Testcase mẫu cho console
Given bài OOP `shape-hierarchy` đã được seed testcase
When người dùng mở tab Console
Then có nút "Chạy mẫu" điền input mẫu của testcase công khai đầu tiên vào ô nhập
And bấm Send trả kết quả chạy với mã nguồn hiện tại.

---

## Error Handling

| Error Condition | HTTP Code | Thông điệp người dùng |
|-----------------|-----------|----------------------|
| Chưa đăng nhập | 401 | "Please log in to continue" |
| Bài không phải `OOP_GRINDING` | 400 | "Console is only available for OOP problems" |
| Bài không tồn tại | 404 | "Problem not found" |
| Không có file `.java` trong payload | 400 | "No Java source files provided" |
| Payload vượt 20MB / >100 file | 413 | "Payload exceeds the allowed limit" |
| Đường dẫn file không hợp lệ (Zip Slip) | 400 | "Invalid file path" |
| Mã nguồn lỗi biên dịch | 200 | `compileErrors` trong payload (không phải lỗi HTTP) |
| Docker không khả dụng | 503 | "Docker sandbox is unavailable" |
| Lệnh vượt timeout 3s | 200 | `exitCode != 0`, `stderr` ghi "Execution timed out" |
| Output vượt 64KB | 200 | `truncated = true` |
| Vượt 50 lệnh/session | 429 | "Console command limit reached for this session" |
| Console trong kỳ thi | 403 | "Console is disabled during the exam" |
| Không đúng chủ sở hữu transcript (không phải staff) | 403 | "You do not have permission to view this transcript" |
| Token nội bộ sai/không có | 401 | "Invalid internal token" |

---

## Implementation TODO

### Slice A — Số đo Runtime / Peak memory thật cho bài OOP (fix bug chính)
- [ ] `backend/.../judge/oop_grinding/runner/OopContainerExecutor.java` (mới): chạy 1 container ephemeral cho OOP, đo wall-clock qua `docker inspect` + peak memory qua sentinel `__MEM_PEAK_KB__` (tái dùng `MemoryProbe`), trả `OopExecutionMetrics(runtimeMs, peakMemoryKb, exitCode, stdout, stderr)`.
- [ ] `backend/.../judge/oop_grinding/model/OopExecutionMetrics.java` (mới).
- [ ] `OopGrindingJudgeStrategy.recordExecutionResults()`: truyền `runtimeMs`/`peakMemoryKb` thật vào `updateSubmissionVerdict(...)`; dùng `null` thay vì `0` khi không đo được.
- [ ] `OopScoreCalculator.formatScoreBreakdownJson()`: thêm `runtimeMs` + `peakMemoryKb` vào JSON breakdown.
- [ ] Unit test: `OopContainerExecutorTest` (không cần Docker — mock `ProcessLauncher`), `OopGrindingJudgeStrategyTest` bổ sung assert số đo được ghi đúng.

### Slice B — Judge worker thành HTTP service + endpoint nội bộ
- [ ] `backend/mini-leetcode-judge/pom.xml`: thêm `spring-boot-starter-web`.
- [ ] `backend/mini-leetcode-judge/src/main/resources/application.yml`: `spring.main.web-application-type: servlet`, `server.port: ${JUDGE_SERVER_PORT:8081}`, `app.judge.internal-token: ${JUDGE_INTERNAL_TOKEN:...}`, `app.console.*` (limits).
- [ ] `judge/console/ConsoleExecutionController.java` (mới): `POST /internal/console/execute`, kiểm tra `X-Internal-Token` → `401` nếu sai.
- [ ] `judge/console/ConsoleSessionService.java` (mới): quản lý compile cache (theo hash mã nguồn) + giới hạn số lệnh + transcript Redis.
- [ ] `judge/console/ConsoleRequest.java` / `ConsoleResponse.java` (mới, record).
- [ ] `judge/console/ExpressionReplHarness.java` (mới): sinh harness reflection cho REPL biểu thức khi không có `main`.
- [ ] `docker-compose.yml`: thêm `JUDGE_INTERNAL_TOKEN` cho cả `api` và `judge`; **không** publish port của judge ra host.
- [ ] Unit test: `ConsoleExecutionControllerTest` (401 khi thiếu/sai token), `ConsoleSessionServiceTest` (giới hạn 50 lệnh, cache hash).

### Slice C — API public + proxy + transcript
- [ ] `mini-leetcode-core/.../console/dto/ConsoleExecuteRequest.java`, `ConsoleExecuteResponse.java`, `ConsoleTranscriptResponse.java` (mới).
- [ ] `mini-leetcode-core/.../console/service/ConsoleService.java` + `ConsoleServiceImpl.java` (mới): kiểm tra bài là `OOP_GRINDING`, kiểm tra `examMode` (qua `contestId`/proctoring), validate file, gọi judge worker qua `RestClient` với header token, đọc/ghi transcript Redis.
- [ ] `mini-leetcode-api/.../console/controller/ConsoleController.java` (mới):
  - `POST /api/v1/problems/{problemId}/console/execute`
  - `GET /api/v1/problems/{problemId}/console/transcript`
  - `DELETE /api/v1/problems/{problemId}/console/transcript`
- [ ] `SecurityConfig`: yêu cầu `authenticated()` cho `/api/v1/problems/*/console/**`; thêm rule chặn console khi contest đang diễn ra.
- [ ] Cấu hình `app.judge.base-url` + `app.judge.internal-token` trong `mini-leetcode-core/application.yml` và `docker-compose.yml` (biến `JUDGE_BASE_URL=http://mini-leetcode-judge:8081`).
- [ ] Unit/integration test: `ConsoleControllerTest` (403 bài ALGORITHMIC, 403 exam mode, 401 chưa đăng nhập, 200 happy path với `RestClient` mock).

### Slice D — Frontend tab Console
- [ ] `frontend/src/types/api.ts`: thêm `ConsoleExecuteResponse`, `ConsoleTranscriptEntry`, `ConsoleExecuteRequest`.
- [ ] `frontend/src/lib/services.ts`: thêm `executeConsoleCommand()`, `fetchConsoleTranscript()`, `clearConsoleTranscript()`.
- [ ] `frontend/src/components/workspace/console-panel.tsx` (mới): ô nhập đa dòng, nút Send / Clear / "Chạy mẫu", vùng log monospace, hiển thị `runtimeMs` + `peakMemoryKb` của lệnh vừa chạy, trạng thái loading/error.
- [ ] `frontend/src/components/workspace/workspace.tsx`: thêm `TabsTrigger value="console"` chỉ khi `isOopGrinding && !examMode && !locked`; render `ConsolePanel` với `multiFiles` hiện tại.
- [ ] `frontend/src/components/workspace/result-panel.tsx` + `registry/problem-detail/problem-result.tsx`: không cần sửa logic (số đo đã đúng từ backend) — chỉ xác nhận hiển thị `Runtime`/`Peak memory`.
- [ ] Accessibility: `aria-label` cho ô nhập, vùng log có `role="log"`, `aria-live="polite"`.

### Slice E — Seed testcase mẫu cho 2 bài OOP
- [ ] `backend/tools/problems/oop_samples.py` (mới): sinh input mẫu cho `shape-hierarchy` và `socket-echo-server`.
- [ ] `backend/tools/seed-oop-console-samples.ps1` (mới): nạp testcase qua API `POST /api/v1/problems/{id}/test-cases` (testcase lưu trên MinIO ⇒ không seed bằng SQL), chỉ tạo testcase công khai (`isHidden=false`) dùng làm mẫu cho console.
- [ ] Cập nhật `seed-problems.sql` (chạy lại `seed_sql.py`) nếu cần.

### Slice F — Test
- [ ] `frontend/e2e/console-oop.spec.ts` (mới): dùng `setupMockApi` mở rộng route `/api/v1/problems/*/console/execute` và `/console/transcript`; kiểm tra tab Console hiển thị, gửi lệnh ra stdout, lỗi biên dịch hiển thị lỗi, transcript sống qua reload, tab bị ẩn ở bài ALGORITHMIC.
- [ ] Mở rộng `frontend/e2e/workspace-oop-multifile.spec.ts`: assert banner Test Result hiển thị `Runtime:` khác `0` và `Peak memory:` khác `—` cho bài OOP (mock trả số thật).
- [ ] `frontend/e2e/fixtures/mock-api.ts`: thêm route console + đặt `executionTimeMs`/`memoryUsedKb` khác 0 cho bài OOP.
- [ ] Backend: `mvnw test -pl mini-leetcode-judge`, `-pl mini-leetcode-core`, `-pl mini-leetcode-api` — không regression.

---

## Out of Scope
- WebSocket / SSE cho console (đã chốt HTTP).
- Giữ trạng thái JVM giữa các lệnh (đã chốt 1 container/lệnh).
- Xây runner image `oop-harness.jar` thật cho toàn bộ 4 phase (giải pháp dài hạn cho việc chấm điểm OOP hoàn toàn trong container).
- Console cho bài `ALGORITHMIC`.
- Chấm điểm OOP bằng `test_cases` (đã chốt testcase chỉ làm mẫu console).
- Tính điểm hoặc xếp hạng từ console.

## Open Questions
- (none — tất cả nhánh của design tree đã được chốt qua 4 vòng grilling)
