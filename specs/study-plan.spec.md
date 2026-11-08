# Feature: Study Plan — Project-based Learning & Code/Check trên IDE

> **Trạng thái:** DRAFT v2 — đã chốt một phần sau phỏng vấn vòng 1, còn [câu hỏi vòng 2](#câu-hỏi-phỏng-vấn-vòng-2). **Chưa code.**
> Hướng sản phẩm: học theo **dự án nhiều chặng (project → stages)** như Hyperskill; học viên **tải project về,
> code trên IDE của mình và bấm Check** để server chấm.

## Overview

**Study Plan** là lộ trình do giảng viên soạn. Mỗi plan gồm các **project** thực tế (vd. "Mini Bank Console",
"TCP Chat Room"). Mỗi project chia thành nhiều **stage** nối tiếp nhau: stage sau mở rộng tiếp code của stage trước,
nên khi xong project học viên có một chương trình hoàn chỉnh. Có thể chèn thêm **bài luyện lẻ** (thuật toán / OOP)
giữa các project để luyện kỹ năng cần cho stage tiếp theo.

Học viên có thể làm mỗi stage theo hai cách:
1. **Trên IDE (cách chính):** tải project skeleton → mở bằng IntelliJ / VS Code / Eclipse → code → bấm **Check** (run configuration dựng sẵn) hoặc gõ `mlc check` → kết quả từ server hiện ngay trong terminal của IDE.
2. **Trên web:** workspace nhiều file hiện có (dùng khi không cài được JDK).

## Tham khảo

| Nguồn | Học được gì | Áp dụng |
|---|---|---|
| [Hyperskill — Study plan](https://hyperskill.org/study-plan) | Plan = kiến thức (topics) + **project** áp dụng. Tiến độ tính theo *learned topics* / *applied topics*. Xong project ⇒ chứng nhận | Plan gồm project + bài luyện lẻ; hiển thị 2 chỉ số tiến độ |
| [Hyperskill Academy plugin cho JetBrains IDE](https://blog.jetbrains.com/education/2025/12/09/hyperskill-gets-its-own-plugin-inside-jetbrains-ides/), [hướng dẫn cài IntelliJ](https://support.hyperskill.org/hc/en-us/articles/360038633072-How-to-install-and-run-IntelliJ-IDEA-for-learning-Java-or-Kotlin) | Đăng nhập trong IDE, nhận task, nút **Check** gửi lời giải, pass ⇒ mở stage tiếp theo | Trải nghiệm "Check ngay trong IDE"; plugin riêng để dành cho v2 |
| [hs-test (Hyperskill)](https://github.com/hyperskill/hs-test) — [Writing tests](https://github.com/hyperskill/hs-test/wiki) | Chấm chương trình console theo từng stage: `StageTest` + `TestedProgram` (bơm stdin, đọc stdout) + `CheckResult.correct()/wrong(msg)`, chạy trên JUnit | Định dạng test cho stage OOP console; thông báo lỗi dễ hiểu kiểu `wrong("Sau lệnh BALANCE 1 cần in …")` |
| [JetBrains Academy — Educator guide](https://plugins.jetbrains.com/plugin/10081-jetbrains-academy/docs/educator-start-guide.html), [course template](https://github.com/jetbrains-academy/python-course-template) | Khoá học khai báo bằng file (`course-info.yaml`, `task-info.yaml`, `task.md` + `tests/`); *framework lesson* = các task dùng chung code; file có thể khoá `editable: false` | Manifest `.mlc/project.yaml` trong skeleton; test public chỉ đọc |
| [CodeCrafters — How challenges work](https://docs.codecrafters.io/challenges/how-challenges-work.md), [CLI usage](https://docs.codecrafters.io/cli/usage) | Project chia stage theo lối TDD; `codecrafters test` chạy nhanh, `--previous` chạy lại các stage trước; `submit` chấm trên server rồi mở stage sau; kết quả đẩy realtime qua WebSocket | Lệnh `mlc check` / `mlc submit`; **chấm cộng dồn** các stage trước; stream log |
| [Exercism CLI](https://github.com/exercism/cli) | Một file binary duy nhất, `download` / `submit`, xác thực bằng **API token**; mỗi ngôn ngữ một test-runner Docker | CLI không cần cài thêm gì; dùng **Personal Access Token** |
| [GitHub Classroom — Autograding](https://docs.github.com/en/education/manage-coursework-with-github-classroom/teach-with-github-classroom/use-autograding), [Assignment overview](https://docs.github.com/en/education/manage-coursework-with-github-classroom/teach-with-github-classroom/monitor-students-progress-with-the-assignment-overview-page) | Classroom → roster → assignment (+ deadline); autograde mỗi lần push; bảng học viên × điểm, xuất CSV. Cảnh báo: sửa test giữa khoá học ảnh hưởng tới repo của học viên đang làm | Mô hình **Lớp** (giai đoạn 2), dashboard và xuất CSV; quản lý phiên bản stage |

## Hiện trạng tái sử dụng (đã kiểm chứng trong code)

| Có sẵn | Vị trí | Dùng cho |
|---|---|---|
| Starter ZIP: `GET /api/v1/problems/{idOrSlug}/starter-template` | `ProblemController` | Nền cho tải project skeleton |
| Nộp ZIP / nhiều file: `POST /api/v1/problems/{id}/submit-multipart`, `/run-multipart` | `SubmissionController` | `mlc submit` / `mlc check` gửi thẳng vào đây, không cần đường chấm mới |
| Chấm OOP: rule cấu trúc + stdin/stdout + socket, `score_breakdown` JSON; cấu hình chấm chỉ ở server | `OopGrindingJudgeStrategy`, `problems.grading_config` | Chấm từng stage, trả lỗi chi tiết về CLI |
| Console live qua WebSocket | `ConsoleWebSocketHandler` | (tuỳ chọn) stream log chấm về CLI |
| `UserProgress` DONE / IN_PROGRESS / NEVER | `ProblemService.resolveProgress` | Tiến độ bài lẻ và stage |
| Starter OOP mẫu `mini-bank-console` (6 lớp) | `backend/tools/problems/oop-starters/` | Tách thành project mẫu 5 stage |
| JWT access token **30 phút**, refresh 7 ngày | `application.yml` | ⚠ Không hợp cho CLI ⇒ cần Personal Access Token |

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|---|---|
| A1 | Mục đích | Tự học theo lộ trình, kiểu **project-based** như Hyperskill |
| A2 | Ai tạo plan | **ADMIN / INSTRUCTOR**; học viên chỉ tham gia |
| A5 | Cách làm bài | **Setup code và check ngay trên IDE** (web workspace vẫn dùng được) |

## Mô hình nghiệp vụ (đề xuất)

```
StudyPlan (DRAFT → PUBLISHED → ARCHIVED, do INSTRUCTOR/ADMIN tạo)
 └─ PlanItem (có thứ tự) ──┬─ Project ── Stage 1..N   (mỗi Stage trỏ tới 1 Problem OOP_GRINDING / ALGORITHMIC)
                           └─ Problem lẻ (bài luyện)
Enrollment (user × plan, started_at)        ← tiến độ tính trực tiếp từ submissions
StageProgress = suy ra từ submission AC của Problem gắn với stage
```

- **Stage = một `Problem`** có sẵn đề Markdown, testcase, `grading_config` và starter ⇒ dùng lại 100% judge. Bảng mới chỉ lưu *thứ tự*: `projects`, `project_stages(project_id, stage_no, problem_id)`, `study_plans`, `study_plan_items`, `study_plan_enrollments`.
- **Code mang sang stage sau:** học viên làm tiếp trong cùng thư mục project trên máy. Khi sang stage mới, CLI chỉ tải thêm *đề + test public* của stage đó, **không ghi đè code** của học viên.
- **Chấm cộng dồn:** stage N chấm cả test của stage 1..N, để tính năng cũ không bị hỏng khi thêm tính năng mới (giống `--previous` của CodeCrafters).
- **Mở khoá tuần tự trong project:** phải pass stage N-1 mới làm được stage N. Các item ở cấp plan thì làm tự do.

## Thiết kế "Setup code & Check trên IDE"

### So sánh phương án

| Phương án | UX | Công sức | Chạy trên IDE nào | Đánh giá |
|---|---|---|---|---|
| **P1. CLI `mlc` + run config dựng sẵn trong skeleton** | Bấm ▶ "Check" trong IDE hoặc gõ `mlc check` | Thấp–vừa (CLI Java, dùng lại API) | Mọi IDE và terminal | **Đề xuất cho MVP** |
| P2. Plugin IntelliJ (như Hyperskill Academy) | Tốt nhất: cây stage, đề bài, nút Check trong IDE | Cao (Kotlin, IntelliJ Platform SDK, phát hành Marketplace) | Chỉ JetBrains | Để dành v2, dùng lại API của P1 |
| P3. Extension VS Code | Tốt | Vừa–cao (TypeScript) | Chỉ VS Code | v2 nếu nhiều học viên dùng VS Code |
| P4. Git push để chấm (như CodeCrafters) | Quen thuộc với dev | Cao (cần git server, hook) | Mọi IDE | Không hợp quy mô đồ án |

### P1 — Luồng chi tiết

```
Web: Study Plan › Mini Bank Console › "Mở trên IDE"
  └─ hiện lệnh:  mlc start mini-bank-console          (hoặc tải ZIP skeleton)

mini-bank-console/                  ← skeleton tải về
├─ pom.xml                          (Java 17, JUnit 5; mở là chạy trên IntelliJ / VS Code / Eclipse)
├─ src/main/java/...                (code của học viên — CLI không bao giờ ghi đè)
├─ src/test/java/stage1..N/         (test public từng stage, chỉ đọc)
├─ stages/stage-1.md ...            (đề từng stage, offline)
├─ .mlc/project.yaml                (planSlug, projectSlug, stage hiện tại, problemId từng stage, server URL)
├─ .run/Check.run.xml               (IntelliJ: nút ▶ "Check" = mlc check)
├─ .vscode/tasks.json               (VS Code: task "Check")
└─ mlc / mlc.cmd                    (wrapper gọi mlc.jar — chỉ cần JDK 17 đã có, không cài thêm)
```

| Lệnh | Việc làm | API dùng |
|---|---|---|
| `mlc login` | Dán **Personal Access Token** tạo trên web (Hồ sơ › Token), lưu tại `~/.mlc/credentials` | Mới: `POST /api/v1/me/tokens` (web) |
| `mlc start <project>` | Tải skeleton + stage đang mở | Mở rộng `starter-template` hoặc mới `GET /projects/{slug}/skeleton` |
| `mlc test` | Chạy **test public trên máy** (`mvn -q test -Dgroups=stage1..N`), offline, không tính điểm | — |
| `mlc check` | Zip `src/` ⇒ nộp chính thức ⇒ chờ kết quả ⇒ in verdict, điểm, rule OOP sai, testcase sai đầu tiên. Pass ⇒ gợi ý `mlc next` | `POST /problems/{id}/submit-multipart` + `GET /submissions/{id}` |
| `mlc next` | Tải đề + test public của stage sau, cập nhật `project.yaml` | Mới: `GET /projects/{slug}/stages/{n}` |
| `mlc status` | Tiến độ plan / project | Mới: `GET /study-plans/{slug}/progress` |

**Ranh giới tin cậy:** test chạy trên máy chỉ để học viên tự kiểm tra. **Chỉ verdict từ server mới được tính.** Test ẩn, `grading_config` và rule OOP vẫn nằm trên server, như quy tắc hiện tại.

## Lớp / nhóm — có cần không?

**Kết luận đề xuất:** MVP **chưa cần** bảng lớp, nhưng thiết kế sẵn để thêm ở giai đoạn 2 mà không phải sửa bảng plan.

| Câu hỏi | Nếu "Có" ⇒ cần Lớp |
|---|---|
| Giảng viên cần giao plan cho **đúng một nhóm** học viên, người ngoài không thấy? | Có |
| Cần **hạn nộp** theo từng đợt / lớp? | Có |
| Giảng viên cần bảng **học viên × stage** của riêng lớp mình, xuất CSV để chấm điểm? | Có |
| Plan công khai, ai cũng tự học, giảng viên chỉ cần xem tổng quan? | Không |

- **MVP (không lớp):** plan công khai; giảng viên xem danh sách người tham gia và tiến độ của plan mình tạo.
- **Giai đoạn 2 — "Lớp gọn" (học từ GitHub Classroom):** `classes(name, instructor_id, join_code)`, `class_members`, `class_plan_assignments(class_id, plan_id, due_at NULL)`. Học viên vào lớp bằng mã; plan gán cho lớp thì chỉ thành viên thấy; dashboard học viên × stage + CSV. Các bảng plan / project / stage không đổi.
- Kỳ thi (`contests`) vẫn giữ riêng, không gộp vào lớp.

## Functional Requirements (nháp)

- **FR-SP-001** When học viên mở `/study-plans`, the system shall liệt kê plan `PUBLISHED` với số project, số bài lẻ, ước lượng giờ học, và % tiến độ của học viên nếu đã tham gia.
- **FR-SP-002** When học viên mở một plan, the system shall hiển thị các item theo thứ tự; với project thì hiện các stage kèm trạng thái `LOCKED` / `NEVER` / `IN_PROGRESS` / `DONE`.
- **FR-SP-003** When học viên bấm "Bắt đầu", the system shall tạo enrollment và mở khoá stage 1 của mọi project trong plan.
- **FR-SP-004** While stage N-1 chưa `DONE`, the system shall từ chối nộp chính thức cho stage N (`409 STAGE_LOCKED`), cả trên web lẫn CLI.
- **FR-SP-005** When một bài nộp chính thức cho stage N đạt `AC`, the system shall đánh dấu stage N `DONE` và mở khoá stage N+1.
- **FR-SP-006** When chấm stage N, the system shall chạy cả testcase và rule của stage 1..N (chấm cộng dồn).
- **FR-SP-007** Where người dùng có vai trò `INSTRUCTOR` / `ADMIN`, the system shall cho phép tạo / sửa plan, project, gắn `Problem` `PUBLISHED` làm stage và sắp thứ tự.
- **FR-IDE-001** When học viên tạo token trên web, the system shall hiện token **một lần duy nhất**, chỉ lưu bản băm, cho đặt hạn dùng và thu hồi.
- **FR-IDE-002** When CLI gọi API với header `Authorization: Bearer mlc_pat_…`, the system shall xác thực như người dùng đó, nhưng **chỉ** cho phép các endpoint đọc plan / project / stage, tải skeleton, nộp bài và xem kết quả.
- **FR-IDE-003** When học viên tải skeleton, the system shall trả ZIP có cấu trúc Maven, test public của các stage đã mở, `.mlc/project.yaml`, run configuration cho IntelliJ / VS Code, và wrapper `mlc`.
- **FR-IDE-004** When `mlc check` nhận kết quả, the system shall in verdict, điểm từng phần (OOP / testcase / socket), rule và testcase sai đầu tiên kèm thông báo dễ hiểu.
- **FR-IDE-005** While đang trong kỳ thi, the system shall không chấp nhận token CLI cho nộp bài thi (kỳ thi chỉ làm trên web có giám sát).

## Acceptance Criteria (nháp)

- **AC-001 Check từ IDE:** Given học viên đã `mlc login`, đang ở stage 2 của Mini Bank · When bấm ▶ Check trong IntelliJ · Then trong ≤ 15 giây terminal hiện `Stage 2: PASSED (100/100)` và gợi ý `mlc next`; trên web stage 2 thành `DONE`.
- **AC-002 Khoá stage:** Given stage 2 chưa `DONE` · When chạy `mlc check` ở stage 3 (sửa tay `project.yaml`) · Then nhận `STAGE_LOCKED`, không tạo submission.
- **AC-003 Không ghi đè code:** Given học viên đã sửa `Bank.java` · When `mlc next` · Then `Bank.java` giữ nguyên; chỉ thêm `stages/stage-3.md` và `src/test/java/stage3/`.
- **AC-004 Chấm cộng dồn:** Given ở stage 3 học viên làm hỏng lệnh `DEPOSIT` của stage 1 · When `mlc check` · Then kết quả `WA`, báo lỗi thuộc test stage 1.
- **AC-005 Token bị thu hồi:** Given token đã bị thu hồi trên web · When `mlc check` · Then `401`, CLI hướng dẫn chạy lại `mlc login`.
- **AC-006 Instructor tạo project:** Given INSTRUCTOR có 5 bài OOP `PUBLISHED` · When tạo project và gắn 5 bài làm stage 1–5 · Then project xuất hiện trong plan, học viên mới chỉ thấy stage 1 mở.

## Project mẫu đề xuất: Mini Bank Console (5 stage)

| Stage | Nội dung | Kỹ năng OOP |
|---|---|---|
| 1 | `Account` + `Main` đọc 1 tài khoản, `DEPOSIT`, `BALANCE` | Lớp, đóng gói, `Scanner` |
| 2 | `abstract Account`, `SavingsAccount`, `CheckingAccount`, `WITHDRAW` theo hạn mức | Kế thừa, đa hình, trừu tượng |
| 3 | `InsufficientFundsException`, lỗi in ra stderr và chạy tiếp | Exception checked |
| 4 | `Bank`: nhiều tài khoản, `OPEN`, `TRANSFER`, `LIST` | Collection, phân tách trách nhiệm |
| 5 | `MONTH`, `REPORT`, `EXIT <mã>` | Template method, hoàn thiện CLI |

## Out of Scope (đề xuất)
- Plugin IntelliJ / extension VS Code (v2, dùng lại API của CLI).
- Lớp / nhóm và hạn nộp (giai đoạn 2, xem phần trên).
- Gợi ý cá nhân hoá, bài kiểm tra đầu vào (diagnostic) như Hyperskill.
- Bài lý thuyết có quiz; chứng nhận PDF.

---

## Câu hỏi phỏng vấn vòng 2

Trả lời theo mã. ⭐ = cần trước khi chốt.

- ⭐ **Q1. Đơn vị học trong plan:** a) Project nhiều stage + bài luyện lẻ xen giữa **(Đề xuất)** · b) Chỉ project · c) Thêm cả bài lý thuyết Markdown (topic) như Hyperskill
- ⭐ **Q2. Cách làm trên IDE cho MVP:** a) CLI `mlc` + run config dựng sẵn **(Đề xuất)** · b) Làm luôn plugin IntelliJ · c) Chỉ tải ZIP về, nộp ZIP trên web (không CLI)
- ⭐ **Q3. Xác thực CLI:** a) Personal Access Token tạo trên web **(Đề xuất)** · b) Đăng nhập trong trình duyệt rồi CLI tự nhận token (device flow — đẹp hơn, tốn công hơn) · c) Gõ username/password trong CLI, dùng refresh token 7 ngày
- ⭐ **Q4. Lớp / nhóm:** a) MVP không có, thiết kế sẵn để thêm sau **(Đề xuất)** · b) Cần ngay ở MVP (mã tham gia lớp, gán plan cho lớp, dashboard) · c) Cần cả hạn nộp theo lớp
- **Q5. Mở khoá stage:** a) Tuần tự, phải pass stage trước **(Đề xuất)** · b) Tự do
- **Q6. Chấm cộng dồn các stage trước:** a) Có **(Đề xuất)** · b) Chỉ chấm stage hiện tại
- **Q7. Thế nào là pass một stage OOP:** a) Verdict `AC` (đủ 100%) **(Đề xuất)** · b) Điểm ≥ ngưỡng do giảng viên đặt (vd. 80)
- **Q8. Ngôn ngữ / build tool cho project:** a) Java 17 + Maven **(Đề xuất)** · b) Thêm Gradle · c) Cả Python
- **Q9. Test public trên máy:** a) Testcase stdin/stdout mẫu + vài JUnit đơn giản; rule OOP chỉ chấm trên server **(Đề xuất)** · b) Cho cả rule OOP chạy trên máy (lộ cấu hình chấm)
- **Q10. Stage có làm được trên web không?** a) Có, cả web lẫn IDE **(Đề xuất)** · b) Chỉ IDE
- **Q11. Sửa stage khi đã có học viên làm** (cảnh báo từ GitHub Classroom): a) Cho sửa đề / test; người đã `DONE` giữ nguyên `DONE` **(Đề xuất)** · b) Khoá sửa, phải tạo project phiên bản mới
- **Q12. Hoàn thành project được gì?** a) Huy hiệu trên hồ sơ **(Đề xuất)** · b) Không có gì · c) Chứng nhận
- **Q13. Project mẫu đầu tiên:** a) Mini Bank Console 5 stage như trên **(Đề xuất)** · b) TCP Chat Room · c) Bạn đề xuất
- **Q14. Hạn chót / mục tiêu demo** của feature này là khi nào? (để chia slice: CLI + 1 project trước, màn hình soạn plan sau)
