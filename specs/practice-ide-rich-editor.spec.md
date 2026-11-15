# Feature: Bài code trong practice, trang stage, Open in IntelliJ, trình soạn trực quan

Nhánh `feature/admin-ux`. Tham khảo: Hyperskill (trang stage "Dolly. Stage 1/8", bài "Change your current directory"), Notion.

## Quyết định đã chốt (phỏng vấn 2026-11-14)

| # | Câu hỏi | Chốt |
|---|---|---|
| Q1 | Bài code nhỏ trong practice chấm thế nào | **Stdin/stdout, một file Java**, ô code nhẹ (CodeMirror, không Monaco), Submit ngay trong trang; hiện Sample Input/Output từ testcase công khai |
| Q2 | "Nút kết nối trực quan" | **Open in IntelliJ một chạm**: web gọi plugin đang chạy; lần đầu hướng dẫn cài + tự kết nối tài khoản (không copy/paste token) |
| Q3 | Trình soạn cho admin | Kiểu Notion: menu thả xuống chọn **Text / Heading 1–3 / danh sách / quote / code**, nút **B I U**, **màu chữ**; gõ `/` để chèn block |
| Q4 | Phạm vi trình soạn | Lý thuyết topic, đề bài, prompt/hint/giải thích của step luyện tập, mô tả plan / item |
| A1 | Lưu nội dung (giả định, chưa hỏi lại) | Vẫn **Markdown**; gạch chân và màu chữ lưu bằng HTML nội dòng trong danh sách cho phép (`<u>`, `<span style="color:#hex">`). Không migrate; plugin và trang học viên đọc được |

## 1. Trình soạn trực quan (`RichTextEditor`)

- TipTap 3 (ProseMirror). Nạp: Markdown → HTML (`marked`) → editor. Lưu: HTML → Markdown (`turndown` + GFM), giữ `<u>` và `<span style="color">`.
- Thanh công cụ: dropdown kiểu block (Text, Heading 1, Heading 2, Heading 3, Bulleted list, Numbered list, Quote, Code block), **B**, *I*, U, `code`, link, màu chữ (bảng 8 màu + mặc định).
- Gõ `/` ở đầu dòng: menu chèn block (lọc theo chữ gõ, ↑/↓, Enter).
- Hai cỡ: `full` (lý thuyết, đề bài: có heading, code block) và `compact` (prompt/hint ngắn: không heading).
- Hiển thị cho học viên: `react-markdown` + `rehype-raw` + `rehype-sanitize` (chỉ cho `u`, `span[style]` với `color:` hợp lệ).

## 2. Bài code trong practice (`CodeExercise`)

- Step CODE của topic (đề ALGORITHMIC) không còn chuyển sang workspace: hiện đề, Sample Input/Output (testcase công khai), "Write a program in Java", ô CodeMirror (số dòng, tô màu Java, Tab thụt lề), **Submit**, kết quả ngay dưới (Accepted / Wrong answer ở sample N / Compile error kèm thông báo).
- Nộp qua `POST /problems/{id}/submit` (language JAVA) rồi poll `GET /submissions/{id}`; AC ⇒ step thành Done (tiến độ đã tính từ bài AC).
- Bản nháp lưu theo bài trong trình duyệt. Đề OOP (nhiều file) hiện nút Open in IntelliJ thay cho ô code.

## 3. Trang stage (`/study-plans/[slug]/[itemId]/[stageNo]`)

Bố cục như Hyperskill: tiêu đề `<Project>. Stage n/N`, link "Project description", thẻ đề (Description, hint), khu làm bài, thẻ "Topics in stage" (chip tới trang topic).
- Đề OOP (project nhiều file): **Open in IntelliJ** (chính) + "Solve in browser" (phụ, mở workspace).
- Đề ALGORITHMIC (mục practice): `CodeExercise` ngay trong trang.
- Trang plan dẫn stage tới trang này thay vì `/problems/[slug]`.

## 4. Open in IntelliJ một chạm

- Plugin đăng ký `RestService` của IntelliJ: `http://localhost:63342/api/mini-leetcode` (cổng 63342–63351 khi chạy nhiều IDE).
  - `GET ?action=ping` → `{version, connected, serverUrl}`
  - `POST ?action=connect` body `{serverUrl, token}` → lưu URL + PAT (PasswordSafe)
  - `POST ?action=open` body `{planSlug, itemId, stageNo}` → đưa IDE lên trước, mở stage (thư mục mặc định `~/MiniLeetCode/<plan>`, hỏi lần đầu)
  - Body gửi `text/plain` (không preflight CORS); trả `Access-Control-Allow-Origin` cho origin gọi tới. Origin lạ: IntelliJ hỏi người dùng cho phép.
- Nút trên web (`IdeButton`): đang dò → **Install plugin** (chưa thấy IDE) → **Connect IntelliJ** (tạo PAT "IntelliJ (one-click)" 90 ngày rồi gửi `connect`) → **Open in IntelliJ** (● Connected).

## Acceptance Criteria

- **AC-1** Admin chọn "Heading 2", gõ chữ, tô đỏ một từ, gạch chân một từ ⇒ lưu ra Markdown có `## `, `<span style="color:#dc2626">`, `<u>`; mở lại hiện y như cũ; học viên thấy đúng màu / gạch chân; `<script>` không chạy.
- **AC-2** Gõ `/` ở dòng trống ⇒ menu block; chọn "Bulleted list" ⇒ dòng thành danh sách.
- **AC-3** Step CODE (ALGORITHMIC) hiện Sample Input/Output và ô code; Submit sai ⇒ "Wrong answer" ngay trong trang; Submit đúng ⇒ "Accepted", step Done, không rời trang.
- **AC-4** Trang stage OOP hiện "Open in IntelliJ"; IDE chưa chạy ⇒ "Install plugin"; đã cài chưa kết nối ⇒ "Connect IntelliJ" ⇒ sau đó "Open in IntelliJ".
- **AC-5** Plugin nhận `connect` ⇒ Settings có URL + token; nhận `open` ⇒ IDE mở đúng stage.

## Tiến độ

- [x] 1 Trình soạn trực quan: `RichTextEditor` (TipTap 3, marked/turndown), gắn vào lý thuyết, prompt/hint/giải thích, đề bài, mô tả plan/item; học viên dùng `Markdown` (rehype-sanitize + chỉ giữ `color`). E2E: AC-1, AC-2, chặn script
- [x] 2 CodeExercise: đề + Sample Input/Output (testcase công khai) + CodeMirror Java + Submit/kết quả ngay trong bài (dùng lại `useJudge`); step CODE của topic dùng nó. E2E AC-3
- [x] 3 Trang stage `/study-plans/[slug]/[itemId]/[stageNo]`: tiêu đề `Project. Stage n/N`, Project description, đề + khu làm bài (CodeExercise hoặc Open in IntelliJ + Solve in browser), Topics in stage, chuyển stage. Trang plan dẫn tới đây. E2E AC-4 + stage khoá
- [x] 4 Open in IntelliJ: plugin `IdeBridgeService` (RestService `/api/mini-leetcode` ping/connect/open; serverUrl phải trùng Origin; chỉ site đã kết nối mới mở được stage) + `IdeButton` trên web (dò cổng 63342–63351, Install → Connect → Open). Test plugin cho quy tắc origin
