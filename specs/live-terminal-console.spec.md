# Feature: Live Terminal Console (stdin/stdout như terminal của IDE)

> Thay thế toàn bộ phần `FR-CONS-*` của `oop-console-runtime-metrics.spec.md`
> (console HTTP từng lệnh + REPL biểu thức + transcript Redis).
> Phần `FR-METRIC-*` (Runtime / Peak memory thật cho bài OOP) **giữ nguyên**.

## Overview

Tab **Console** ở khung dưới workspace hoạt động như terminal "Run" của IDE:
bấm **Run** → chương trình của học viên (code đang có trong editor) chạy trong
Docker sandbox, output hiện ra **ngay khi chương trình in**; khi chương trình
chờ `Scanner.nextInt()` / `input()` thì học viên gõ một dòng vào terminal,
Enter → dòng đó được đẩy vào `stdin` của tiến trình đang chạy.

Giá trị: học viên thử chương trình tương tác (menu, nhập n rồi nhập n số, prompt
"Enter name:"…) giống hệt khi chạy trên máy mình, không phải đoán trước toàn bộ input.

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| D1 | Mức tương tác | **Live** — WebSocket, stdin gửi từng dòng khi chương trình đang chạy |
| D2 | Phạm vi | Bài `OOP_GRINDING` + `ALGORITHMIC`, **chỉ Java** (bài thuật toán: khi editor đang chọn Java; Python để sau) |
| D3 | REPL biểu thức (`EVALUATE`) | **Bỏ** — console chỉ chạy `main` / script |
| D4 | Đường đi WebSocket | Browser ⇄ API `/api/v1/console/ws` (JWT) ⇄ judge `/internal/console/ws` (`X-Internal-Token`) ⇄ `docker run -i` |
| D5 | Giới hạn | 120s wall/lần chạy; kill sau 30s không có stdin & không có output; 1 tiến trình/user (Run mới kill cái cũ); output ≤ 64KB; 256MB / 1 CPU; ≤ 30 lần Run / 10 phút / user; ≤ 20 container console đồng thời / judge |
| D6 | Lịch sử | **Không lưu** — reload là mất, như terminal IDE. Không Redis transcript |
| D7 | Điều khiển | Không có nút nào trong console: nút **Run** của workspace chạy/chạy lại; `Ctrl+D` đóng stdin; `Ctrl+C` dừng tiến trình |
| D8 | Thông tin sau khi chạy | `Process finished with exit code N (x.xs)`; không hiện peak memory (thời gian chờ người gõ làm số đo vô nghĩa) |

## Giao thức WebSocket (JSON text frame)

Browser → API (frame đầu tiên bắt buộc là `start`):

```json
{"type":"start","token":"<JWT>","problemId":1,"language":"JAVA","files":[{"path":"Solution.java","content":"..."}]}
{"type":"stdin","data":"5\n"}
{"type":"eof"}
{"type":"kill"}
```

Server → Browser:

```json
{"type":"stdout","data":"Enter n: "}
{"type":"stderr","data":"Exception in thread ..."}
{"type":"exit","code":0,"elapsedMs":3120,"reason":null}
{"type":"error","message":"Console is disabled during the exam"}
```

`reason` ∈ `null | "timeout" | "idle" | "output-limit" | "killed" | "compile-error"`.
API ⇄ judge dùng cùng định dạng; API thay `token` bằng `userId` và chỉ chuyển tiếp
`stdin` / `eof` / `kill` từ browser (mọi `start` thứ hai bị bỏ qua).

## Functional Requirements (EARS)

- **FR-LC-001** Where the user is not in exam mode and the session is not locked, the workspace shall show a "Console" tab for both `ALGORITHMIC` and `OOP_GRINDING` problems.
- **FR-LC-002** When the user presses the workspace **Run** button (Java code, not in exam), the workspace shall switch to the Console tab and the console shall open a WebSocket to `/api/v1/console/ws` and send a `start` frame with the current editor files and language (OOP ⇒ `JAVA` + all files; algorithmic ⇒ selected language + its single file).
- **FR-LC-003** When the API receives a `start` frame, it shall reply with an `error` frame and close if the JWT is missing/invalid, the problem does not exist, the problem belongs to a running contest, the language has no runtime, or file paths/sizes are invalid.
- **FR-LC-004** When a user starts a run while another run of theirs is active, the system shall kill the previous run.
- **FR-LC-005** If a user has started 30 runs in the current 10-minute window, then the API shall reply `error` "Console run limit reached, try again later".
- **FR-LC-006** When the judge receives a valid `start`, it shall compile the Java sources (in-process javac, no execution) and, on failure, stream the compiler output as `stderr` then send `exit` with `reason="compile-error"` without starting a container.
- **FR-LC-007** When compilation succeeds, the judge shall start `docker run -i` with the sandbox flags (`--network none`, `--memory 256m`, `--memory-swap 256m`, `--cpus 1.0`, `--pids-limit 64`, `--read-only`, `--tmpfs /tmp`, `--cap-drop ALL`, `--security-opt no-new-privileges:true`, `--user 1000:1000`, workspace mounted read-only) running the detected `main` class.
- **FR-LC-008** While the process runs, the judge shall forward every stdout/stderr chunk as soon as it is read (no line buffering), so prompts without a trailing newline appear immediately.
- **FR-LC-009** When a `stdin` frame arrives, the judge shall write its data to the process stdin and flush.
- **FR-LC-010** When an `eof` frame arrives, the judge shall close the process stdin.
- **FR-LC-011** When a `kill` frame arrives or either WebSocket closes, the judge shall `docker kill` the container and remove it.
- **FR-LC-012** If a run exceeds 120s wall time, 30s idle (no stdin and no output), or 64KB of output, then the judge shall kill it and send `exit` with the matching `reason`.
- **FR-LC-013** When the process exits, the judge shall send `exit` with the exit code and elapsed time, then close the socket.
- **FR-LC-014** If 20 console containers are already running on the judge, then the judge shall reply `error` "Console is busy, try again in a moment".
- **FR-LC-015** If Docker is unavailable, then the judge shall reply `error` "Docker sandbox is unavailable" and shall never run code in-process.
- **FR-LC-016** The console shall be a bare terminal (no buttons, no hints): stdin is typed inline after the program output and echoed, stderr is red, Enter sends a line, Ctrl+D closes stdin, Ctrl+C (no selection) stops the process; pressing Run again restarts the program. The input exists only while a process is running.
- **FR-LC-017** Where the console cannot run the code (exam mode, or a non-Java language), the Run button shall keep the previous behaviour (quick run against testcases).

## Non-Functional Requirements

- **Latency**: first output of a Java hello-world ≤ 3s p95 after Run (javac in-process + JVM start); stdin round trip ≤ 200ms on LAN.
- **Security**: user code only runs inside the sandbox container; judge WS endpoint requires `X-Internal-Token` (constant-time compare) and is never published; JWT is sent in the first frame, never in the URL (keeps it out of access logs); browser frames other than `stdin`/`eof`/`kill` after `start` are dropped; source limits 100 files / 1MB (WebSocket frame ≤ 2MB — Tomcat cấp buffer theo session nên không để 20MB); `stdin` frame ≤ 64KB.
- **Resource safety**: every exit path (normal exit, kill, timeout, socket drop, judge error) removes the container (`docker rm -f`).
- **Observability**: log `userId`, `problemId`, `language`, exit code, reason, elapsed for every run.

## Acceptance Criteria

- **AC-1 Scanner live input** — Given Java `main` printing `Enter n: ` then `sc.nextInt()` then printing `n*2`, When the user presses Run, Then `Enter n: ` appears before any input is typed, And after typing `5` + Enter the terminal shows `10` and `Process finished with exit code 0`.
- **AC-2 Non-Java language** — Given an algorithmic problem with Python selected, When the Console tab opens, Then Run is disabled and the panel says the console runs Java only.
- **AC-3 End of input** — Given a program looping on `while (sc.hasNextLine())`, When the user types two lines and presses Ctrl+D, Then the program receives both lines and exits.
- **AC-4 Compile error** — Given `Solution.java` missing `;`, When Run, Then stderr shows `Solution.java:<line>: error: ';' expected` and exit reason `compile-error`; no container is created.
- **AC-5 Stop** — Given `while(true){}`, When Stop is pressed, Then `exit` arrives within 3s and `docker ps -a` has no container left.
- **AC-6 Idle** — Given a program waiting on `sc.nextLine()`, When the user types nothing for 30s, Then the run ends with reason `idle`.
- **AC-7 Exam lock** — Given a problem in a running contest, When `start` is sent, Then an `error` frame "Console is disabled during the exam" arrives and nothing runs; the tab is also hidden in exam mode.
- **AC-8 One per user** — Given a run is active, When the user presses Run again, Then the first container is killed before the second starts.
- **AC-9 Bad token** — Given a judge WS handshake without a valid `X-Internal-Token`, Then the handshake is rejected (HTTP 401).
- **AC-10 Algorithmic Java** — Given the default Java template on an algorithmic problem, When Run, typing the sample input and pressing Ctrl+D, Then the program output appears.

## Error Handling

| Condition | Where | Frame / UI |
|-----------|-------|-----------|
| JWT missing/invalid/expired | API | `error` "Please log in to use the console" → close |
| Problem not found | API | `error` "Problem not found" |
| Problem in running contest | API | `error` "Console is disabled during the exam" |
| Unsupported language | API | `error` "Language X cannot run in the console" |
| Invalid path / too many files / >1MB | API | `error` with validator message |
| Rate limit | API | `error` "Console run limit reached, try again later" |
| Judge unreachable | API | `error` "Console service is unavailable" |
| First frame not `start` / malformed JSON | API, judge | `error` "Invalid console message" → close |
| Bad internal token | judge | handshake 401 |
| Docker unavailable | judge | `error` "Docker sandbox is unavailable" |
| Too many concurrent containers | judge | `error` "Console is busy, try again in a moment" |
| Compile error | judge | `stderr` + `exit{reason:"compile-error"}` |
| Timeout / idle / output limit / killed | judge | `exit{reason:…}` |
| Browser socket drops | API→judge | judge kills container |

## Implementation TODO

### Judge (`mini-leetcode-judge`)
- [x] `pom.xml`: add `spring-boot-starter-websocket`.
- [x] `console/LiveConsoleHandler` — `TextWebSocketHandler` at `/internal/console/ws`; token check in handshake; parses `start`/`stdin`/`eof`/`kill`.
- [x] `console/LiveConsoleRun` — workspace, compile (reuse `OopGrindingCompiler` + `OopMainClassDetector`), `docker run -i` with sandbox flags, pump stdout/stderr, wall/idle/output limits, always `docker rm -f`.
- [x] `ConsoleProperties`: `maxWallMs`, `idleMs`, `maxOutputBytes`, `maxConcurrent`, `memoryLimitMb`.
- [x] Delete `InternalConsoleController`, `ConsoleSessionService`, `ConsoleReplHarness`, `ConsoleRequest`, `ConsoleResponse`.
- [x] Tests: script per language, bad first frame, limits.

### API (`mini-leetcode-api` + `mini-leetcode-core`)
- [x] `spring-boot-starter-websocket` in API.
- [x] `console/ConsoleWebSocketHandler` at `/api/v1/console/ws`: JWT via `JwtTokenProvider`, validation in `ConsoleService.prepare(...)`, rate limit (Redis `INCR` + TTL), one active run per user, relay with `StandardWebSocketClient`.
- [x] `SecurityConfig`: `permitAll` for `/api/v1/console/ws` (auth in first frame).
- [x] Delete `ConsoleController`, `ConsoleTranscriptStore`, `JudgeWorkerClient`, console DTOs, `ConsoleMode`, unused console exceptions + handlers.
- [x] Tests: `ConsoleServiceTest` validation rules.

### Frontend
- [x] `console-panel.tsx`: terminal log + input line, Run/Stop, EOF, Clear, WebSocket client; local echo; Ctrl+D.
- [x] `workspace.tsx`: tab for all problem types (not exam/locked); pass files + language.
- [x] Remove old console types/services/mock routes.
- [x] `e2e/console.spec.ts` with `page.routeWebSocket`.

### Infra
- [x] `docker-compose.yml`: `JUDGE_CONSOLE_WS_URL=ws://mini-leetcode-judge:8081/internal/console/ws`; drop old console envs.

## Out of Scope
- PTY (`docker run -t`): colours/raw-key programs, `Console.readPassword`.
- Python (dùng `python -u` để prompt không bị buffer khi làm) và C++ (chưa có runtime trong `LanguageRuntime`).
- Persisted history, staff transcript review.
- Peak memory display in console.
