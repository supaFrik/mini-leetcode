# Feature: Study Plan — Project-based Learning & Plugin IntelliJ

> **Trạng thái:** DRAFT v3 — đã chốt Q1–Q4 (vòng 2), còn [câu hỏi vòng 3](#câu-hỏi-phỏng-vấn-vòng-3). **Chưa code.**
> Hướng sản phẩm: học theo **project nhiều stage** như Hyperskill; học viên mở project **ngay trong IntelliJ
> bằng plugin Mini LeetCode**, đọc đề, code và bấm **Check** để server chấm.

## Overview

**Study Plan** là lộ trình do INSTRUCTOR / ADMIN soạn, gồm các **project** thực tế (vd. "Mini Bank Console")
xen kẽ **bài luyện lẻ** (thuật toán / OOP). Mỗi project chia thành nhiều **stage** nối tiếp: stage sau mở rộng
code của stage trước, xong project là có một chương trình hoàn chỉnh.

Học viên làm bài theo hai cách:
1. **Plugin IntelliJ (cách chính):** tool window "Mini LeetCode" hiện cây plan → project → stage. Bấm mở stage thì plugin tạo / mở project Maven, hiện đề bên cạnh editor, bấm **Check** ⇒ kết quả server hiện ngay trong IDE ⇒ **Next stage**.
2. **Web workspace** (đã có): dùng khi không có IntelliJ (VS Code, máy phòng lab…).

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|---|---|
| A1 | Mục đích | Tự học theo lộ trình, kiểu **project-based** như Hyperskill |
| A2 | Ai tạo plan | **ADMIN / INSTRUCTOR**; học viên chỉ tham gia |
| A5 | Cách làm bài | Setup code và check **ngay trong IDE**; web workspace vẫn dùng được |
| Q1 | Đơn vị học | **Project nhiều stage + bài luyện lẻ xen giữa** |
| Q2 | Tích hợp IDE ở MVP | **Plugin IntelliJ** (không làm CLI riêng) |
| Q3 | Xác thực IDE | **Personal Access Token** tạo trên web, plugin lưu trong Password Safe của IDE |
| Q4 | Lớp / nhóm | **Chưa làm ở MVP**; thiết kế sẵn để thêm ở giai đoạn 2 |

## Tham khảo

| Nguồn | Học được gì | Áp dụng |
|---|---|---|
| [Hyperskill — Study plan](https://hyperskill.org/study-plan) | Plan = kiến thức + **project** áp dụng; tiến độ *learned / applied topics*; xong project ⇒ chứng nhận | Plan gồm project + bài lẻ; 2 chỉ số tiến độ |
| [Hyperskill Academy plugin](https://blog.jetbrains.com/education/2025/12/09/hyperskill-gets-its-own-plugin-inside-jetbrains-ides/), [hướng dẫn IntelliJ](https://support.hyperskill.org/hc/en-us/articles/360038633072-How-to-install-and-run-IntelliJ-IDEA-for-learning-Java-or-Kotlin) | Đăng nhập trong Settings › Tools; Project View hiện syllabus; nút **Check**; pass ⇒ sang stage sau | Mẫu UX chính cho plugin |
| [hs-test](https://github.com/hyperskill/hs-test) ([wiki](https://github.com/hyperskill/hs-test/wiki)) | Test stage console: `StageTest`, `TestedProgram` (bơm stdin / đọc stdout), `CheckResult.wrong(msg)` | Thông báo lỗi dễ hiểu cho từng stage |
| [JetBrains Academy — Educator guide](https://plugins.jetbrains.com/plugin/10081-jetbrains-academy/docs/educator-start-guide.html), [course template](https://github.com/jetbrains-academy/python-course-template) | Khoá học khai báo bằng file (`course-info.yaml`, `task-info.yaml`, `task.md`, `tests/`); *framework lesson* = các task dùng chung code; file `editable: false` | Manifest `.mlc/project.yaml`; test public chỉ đọc |
| [CodeCrafters](https://docs.codecrafters.io/challenges/how-challenges-work.md) ([CLI](https://docs.codecrafters.io/cli/usage)) | Stage kiểu TDD; chạy lại test các stage trước; server chấm rồi mới mở stage sau | Chấm cộng dồn; server có quyền quyết định |
| [Exercism CLI](https://github.com/exercism/cli) | Xác thực bằng API token; mỗi ngôn ngữ một test-runner Docker | Personal Access Token |
| [GitHub Classroom](https://docs.github.com/en/education/manage-coursework-with-github-classroom/teach-with-github-classroom/use-autograding) ([overview](https://docs.github.com/en/education/manage-coursework-with-github-classroom/teach-with-github-classroom/monitor-students-progress-with-the-assignment-overview-page)) | Classroom → roster → assignment + deadline; bảng học viên × điểm, xuất CSV; sửa test giữa khoá thì ảnh hưởng người đang làm | Lớp ở giai đoạn 2; quản lý phiên bản stage |
| IntelliJ Platform SDK: [Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), [Tool windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html), [Credentials (PasswordSafe)](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html), [JCEF](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html), [Custom plugin repository](https://plugins.jetbrains.com/docs/intellij/custom-plugin-repository.html), [Build number ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) | Cách build plugin, đăng ký tool window, lưu token an toàn, render Markdown, tự phát hành có auto-update | Kiến trúc plugin bên dưới. Lưu ý: dependency `com.intellij.modules.jcef` chỉ chắc chắn dùng được từ 2025.3.1 ([thread](https://platform.jetbrains.com/t/2026-2-is-coming-time-to-check-your-plugin-compatibility/4618)) |

## Hiện trạng tái sử dụng (đã kiểm chứng trong code)

| Có sẵn | Vị trí | Dùng cho |
|---|---|---|
| Starter ZIP `GET /api/v1/problems/{idOrSlug}/starter-template` | `ProblemController` | Nền cho tải skeleton project / stage |
| Nộp ZIP / nhiều file `POST /api/v1/problems/{id}/submit-multipart`, `/run-multipart` | `SubmissionController` | Nút Check gửi vào đây, không cần đường chấm mới |
| `GET /api/v1/submissions/{id}` (verdict, score, `score_breakdown`) | `SubmissionController` | Plugin poll và hiển thị kết quả |
| Chấm OOP: rule + stdin/stdout + socket; cấu hình chấm chỉ ở server | `OopGrindingJudgeStrategy`, `problems.grading_config` | Chấm từng stage |
| `UserProgress` DONE / IN_PROGRESS / NEVER | `ProblemService.resolveProgress` | Tiến độ bài lẻ và stage |
| Starter `mini-bank-console` (6 lớp) | `backend/tools/problems/oop-starters/` | Project mẫu 5 stage |
| JWT access token **30 phút** | `application.yml` | ⚠ Không hợp cho plugin ⇒ Personal Access Token |

**Lỗi nhỏ phát hiện khi khảo sát** (sửa luôn trong feature này): `findAttemptedProblemIdsByUserId` đang tính cả Quick Run vào `IN_PROGRESS`.

## Mô hình nghiệp vụ

```
StudyPlan (DRAFT → PUBLISHED → ARCHIVED; INSTRUCTOR/ADMIN tạo)
 └─ PlanItem (có thứ tự) ──┬─ Project ── Stage 1..N   (mỗi Stage trỏ tới 1 Problem)
                           └─ Problem lẻ (bài luyện)
Enrollment (user × plan, started_at)          ← tiến độ suy ra từ submissions, không lưu trùng
PersonalAccessToken (user, name, hash, expires_at, last_used_at, revoked)
```

- **Stage = một `Problem`** (đề, testcase, `grading_config`, starter có sẵn) ⇒ dùng lại judge 100%. Bảng mới (Flyway `V8`): `study_plans`, `study_plan_items`, `projects`, `project_stages(project_id, stage_no, problem_id)`, `study_plan_enrollments`, `personal_access_tokens`.
- **Code mang sang stage sau:** học viên làm tiếp trong cùng project IntelliJ. Plugin chỉ thêm đề + test public của stage mới, **không ghi đè code** của học viên.
- **Chấm cộng dồn và mở khoá tuần tự trong project** (đề xuất, chờ chốt Q5 / Q6).
- **Giai đoạn 2 — Lớp:** `classes(name, instructor_id, join_code)`, `class_members`, `class_plan_assignments(class_id, plan_id, due_at NULL)`. Bảng plan không phải sửa.

## Thiết kế plugin IntelliJ

### Cấu trúc monorepo

```
ide-plugin/                         ← module mới, build bằng Gradle (tách khỏi Maven backend)
├─ build.gradle.kts                 IntelliJ Platform Gradle Plugin 2.x
├─ src/main/resources/META-INF/plugin.xml
└─ src/main/{java|kotlin}/…/
   ├─ api/          MlcApiClient (HTTP + JSON, header Authorization: Bearer mlc_pat_…)
   ├─ auth/         Settings page + PasswordSafe
   ├─ toolwindow/   Cây Plan › Project › Stage, panel đề, panel kết quả
   ├─ project/      Tạo / mở project từ skeleton, ghép file stage mới
   ├─ check/        CheckAction (Task.Backgroundable: zip → submit → poll → hiển thị)
   └─ model/        DTO dùng chung contract OpenAPI của backend
```

### Chức năng MVP của plugin

| # | Chức năng | Chi tiết |
|---|---|---|
| PL-1 | Đăng nhập | *Settings › Tools › Mini LeetCode*: Server URL + Personal Access Token ⇒ "Test connection" (`GET /api/v1/auth/me`). Token lưu bằng `PasswordSafe`, không ghi ra file |
| PL-2 | Tool window "Mini LeetCode" | Cây Plan › Project › Stage và bài lẻ, icon trạng thái `LOCKED` / `NEVER` / `IN_PROGRESS` / `DONE`, nút Refresh, nút "Bắt đầu plan" |
| PL-3 | Mở project / stage | Chọn thư mục ⇒ tải skeleton ZIP ⇒ giải nén ⇒ mở như Maven project (JDK 17). Đã mở rồi thì chỉ focus |
| PL-4 | Xem đề | Panel đề Markdown ngay trong tool window (JCEF; nếu IDE không có JCEF thì fallback sang HTML Swing) |
| PL-5 | Chạy test trên máy | Nút "Run tests" ⇒ run configuration Maven `test` của các stage đã mở; không tính điểm |
| PL-6 | **Check** | Nút trên toolbar + phím tắt: zip `src/main/**` ⇒ `submit-multipart` ⇒ poll `GET /submissions/{id}` có backoff ⇒ panel kết quả (verdict, điểm OOP / testcase / socket, rule sai, testcase sai đầu tiên) + thông báo balloon |
| PL-7 | Next stage | Pass ⇒ nút "Next stage" ⇒ tải `stages/stage-N.md` + `src/test/java/stageN/`; trùng tên file thì **giữ file của học viên**, file mới lưu kèm hậu tố `.new` và báo cho học viên |
| PL-8 | Bài luyện lẻ | Mở như project 1 stage, cùng luồng Check |
| PL-9 | Lỗi & mạng | `401` ⇒ mở Settings; `409 STAGE_LOCKED`; server sập ⇒ thông báo, không mất code |

### Backend cần thêm cho plugin

| Endpoint | Mục đích |
|---|---|
| `POST/GET/DELETE /api/v1/me/tokens` | Tạo (hiện token một lần), liệt kê, thu hồi Personal Access Token (màn hình trên web) |
| `GET /api/v1/auth/me` | Plugin kiểm tra token |
| `GET /api/v1/study-plans`, `/{slug}`, `/{slug}/progress`, `POST /{slug}/enroll` | Cây plan + tiến độ |
| `GET /api/v1/projects/{slug}/skeleton` | ZIP project Maven: `pom.xml`, `src/main` (starter stage 1), test public các stage đã mở, `stages/*.md`, `.mlc/project.yaml` |
| `GET /api/v1/projects/{slug}/stages/{n}/files` | Đề + test public của stage N (chỉ khi đã mở khoá) |
| Kiểm tra khoá stage trong `submit-multipart` | Trả `409 STAGE_LOCKED` cho cả web lẫn plugin |
| Phân phối plugin: `GET /plugins/updatePlugins.xml` + file ZIP | Kho plugin riêng để IDE tự cập nhật (tuỳ Q-P3) |

**Ranh giới tin cậy:** test chạy trên máy chỉ để tự kiểm tra; **chỉ verdict từ server mới được tính**. Test ẩn và `grading_config` không bao giờ gửi xuống plugin. Token PAT chỉ được gọi các endpoint ở bảng trên và nộp bài luyện tập; **không dùng được cho kỳ thi** (kỳ thi chỉ làm trên web có giám sát).

## Functional Requirements (nháp)

- **FR-SP-001** When học viên mở `/study-plans` (web) hoặc tool window (plugin), the system shall liệt kê plan `PUBLISHED` kèm số project, số bài lẻ và % tiến độ của học viên.
- **FR-SP-002** When học viên mở một plan, the system shall hiển thị item theo thứ tự; project kèm các stage và trạng thái `LOCKED` / `NEVER` / `IN_PROGRESS` / `DONE`.
- **FR-SP-003** When học viên bấm "Bắt đầu", the system shall tạo enrollment và mở khoá stage 1 của mọi project trong plan.
- **FR-SP-004** While stage N-1 chưa `DONE`, the system shall từ chối nộp chính thức stage N với `409 STAGE_LOCKED`.
- **FR-SP-005** When bài nộp chính thức của stage N đạt `AC`, the system shall đánh dấu stage N `DONE` và mở khoá stage N+1.
- **FR-SP-006** When chấm stage N, the system shall chạy testcase và rule của stage 1..N.
- **FR-SP-007** Where người dùng là `INSTRUCTOR` / `ADMIN`, the system shall cho tạo / sửa plan, project, gắn `Problem` `PUBLISHED` làm stage và sắp thứ tự.
- **FR-PAT-001** When học viên tạo token, the system shall hiện token một lần, chỉ lưu bản băm, cho đặt hạn dùng và thu hồi.
- **FR-PAT-002** If request dùng PAT gọi endpoint ngoài danh sách cho phép hoặc nộp bài kỳ thi, then the system shall trả `403`.
- **FR-PL-001** When học viên bấm Check, the plugin shall nộp mã nguồn `src/main/**` của project hiện tại cho đúng stage trong `.mlc/project.yaml` và hiển thị kết quả trong ≤ 2 giây sau khi server chấm xong.
- **FR-PL-002** When tải file stage mới, the plugin shall không ghi đè file đã có của học viên.
- **FR-PL-003** The plugin shall lưu token bằng Password Safe của IDE, không ghi token vào thư mục project.

## Acceptance Criteria (nháp)

- **AC-001 Check trong IntelliJ:** Given đã đăng nhập plugin, đang ở stage 2 Mini Bank · When bấm Check · Then panel kết quả hiện `Stage 2: PASSED (100/100)` và nút "Next stage"; trên web stage 2 thành `DONE`.
- **AC-002 Lỗi hiển thị rõ:** Given `SavingsAccount` không kế thừa `Account` · When Check · Then kết quả chỉ ra rule *"SavingsAccount phải kế thừa Account"*, kèm điểm OOP / testcase.
- **AC-003 Khoá stage:** Given stage 2 chưa `DONE` · When nộp stage 3 (sửa tay `project.yaml`) · Then `409 STAGE_LOCKED`, không tạo submission.
- **AC-004 Không ghi đè code:** Given học viên đã sửa `Bank.java` · When Next stage · Then `Bank.java` giữ nguyên; chỉ thêm đề + test stage mới.
- **AC-005 Chấm cộng dồn:** Given ở stage 3 học viên làm hỏng `DEPOSIT` của stage 1 · When Check · Then `WA`, lỗi được ghi là thuộc stage 1.
- **AC-006 Token bị thu hồi:** Given token bị thu hồi trên web · When Check · Then thông báo "Token hết hiệu lực", mở Settings.
- **AC-007 PAT không vào được kỳ thi:** Given token hợp lệ · When gọi nộp bài có `contestId` · Then `403`.
- **AC-008 Web vẫn làm được:** Given học viên không có IntelliJ · When mở stage trên web · Then làm và nộp như bài OOP hiện tại, tiến độ đồng bộ với plugin.

## Project mẫu: Mini Bank Console (5 stage)

| Stage | Nội dung | Kỹ năng OOP |
|---|---|---|
| 1 | `Account` + `Main`: 1 tài khoản, `DEPOSIT`, `BALANCE` | Lớp, đóng gói, `Scanner` |
| 2 | `abstract Account`, `SavingsAccount`, `CheckingAccount`, `WITHDRAW` theo hạn mức | Kế thừa, đa hình |
| 3 | `InsufficientFundsException`, lỗi in ra stderr và chạy tiếp | Exception checked |
| 4 | `Bank`: nhiều tài khoản, `OPEN`, `TRANSFER`, `LIST` | Collection, phân tách trách nhiệm |
| 5 | `MONTH`, `REPORT`, `EXIT <mã>` | Template method, hoàn thiện |

## Slice đề xuất (thứ tự làm)

1. **Backend nền:** Flyway V8, PAT, API plan / project / stage, khoá stage, sửa lỗi Quick Run.
2. **Nội dung mẫu:** tách Mini Bank thành 5 `Problem` + project + 1 plan seed.
3. **Plugin khung:** Gradle, Settings + PasswordSafe, tool window với cây plan.
4. **Plugin mở project + đề** (PL-3, PL-4).
5. **Plugin Check + kết quả + Next stage** (PL-5…PL-7) ⇒ *mốc demo được*.
6. **Web:** trang `/study-plans`, trang tạo token, màn hình INSTRUCTOR soạn plan.
7. **Test:** unit / integration backend, test plugin (`BasePlatformTestCase`), E2E web.

## Out of Scope
- CLI, extension VS Code (VS Code dùng web workspace).
- Phát hành trên JetBrains Marketplace (MVP cài từ ZIP hoặc kho riêng).
- Lớp / nhóm, hạn nộp (giai đoạn 2).
- Gợi ý cá nhân hoá, bài kiểm tra đầu vào, bài lý thuyết có quiz, chứng nhận PDF.

---

## Câu hỏi phỏng vấn vòng 3

⭐ = cần trước khi chốt. Mỗi câu có **(Đề xuất)**.

### Plugin
- ⭐ **P1. IDE và phiên bản tối thiểu:** a) IntelliJ IDEA (Community + Ultimate) từ **2025.3** trở lên **(Đề xuất** — JCEF ổn định, đủ mới) · b) Từ 2024.3 (máy lab cũ hơn) · c) Mọi IDE JetBrains (PyCharm…)
- ⭐ **P2. Ngôn ngữ viết plugin:** a) **Java** — cả nhóm quen, SDK hỗ trợ đầy đủ **(Đề xuất)** · b) Kotlin — chuẩn của JetBrains, nhiều ví dụ hơn
- ⭐ **P3. Phát hành plugin:** a) Cài từ file ZIP (Settings › Plugins › Install from disk) **(Đề xuất cho MVP)** · b) Kho plugin riêng do backend phục vụ, IDE tự cập nhật · c) JetBrains Marketplace
- **P4. Nút Check có chạy test trên máy trước khi nộp không?** a) Không — Check nộp thẳng lên server, có nút "Run tests" riêng **(Đề xuất)** · b) Có, test trên máy fail thì không nộp
- **P5. Bài luyện lẻ có mở được trong plugin không?** a) Có, coi như project 1 stage **(Đề xuất)** · b) Chỉ project
- **P6. Có cần xem lịch sử bài nộp / code cũ trong plugin không?** a) Không, bấm link mở trang web **(Đề xuất)** · b) Có

### Quy tắc học (còn lại từ vòng 2)
- **Q5. Mở khoá stage:** a) Tuần tự, phải pass stage trước **(Đề xuất)** · b) Tự do
- **Q6. Chấm cộng dồn các stage trước:** a) Có **(Đề xuất)** · b) Chỉ stage hiện tại
- **Q7. Pass stage OOP:** a) Verdict `AC` **(Đề xuất)** · b) Điểm ≥ ngưỡng giảng viên đặt
- **Q8. Ngôn ngữ / build cho project học viên:** a) Java 17 + Maven **(Đề xuất)** · b) Thêm Gradle · c) Cả Python
- **Q9. Test public trên máy:** a) Testcase mẫu + JUnit đơn giản; rule OOP chỉ chấm trên server **(Đề xuất)** · b) Cho cả rule OOP chạy trên máy
- **Q11. Sửa stage khi đã có người học:** a) Cho sửa; người đã `DONE` giữ `DONE` **(Đề xuất)** · b) Khoá, phải tạo phiên bản mới
- **Q12. Hoàn thành project được gì?** a) Huy hiệu trên hồ sơ **(Đề xuất)** · b) Không · c) Chứng nhận
- **Q13. Project mẫu đầu tiên:** a) Mini Bank Console 5 stage **(Đề xuất)** · b) TCP Chat Room · c) Khác
- **Q14. Hạn chót / mốc demo** là khi nào? Ai trong nhóm làm plugin? (plugin là phần rủi ro nhất: SDK mới, cần thời gian học)
