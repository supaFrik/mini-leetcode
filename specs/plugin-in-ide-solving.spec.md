# Spec: Giải bài ngay trong plugin IntelliJ (đề + Run/Check + mở khoá stage)

> Trạng thái: **Draft v1** (2026-11-15) · Nguồn: phỏng vấn người dùng (feature-forge) · Liên quan: `study-plan.spec.md` (PL-3…PL-6), `topics.spec.md`
> Thay đổi hành vi so với `study-plan.spec.md`: PL-4 (mở đề bằng file `stage-N.md`) và PL-6 (Next stage mở project mới) được **thay thế** bởi spec này.

## 1. Overview

Hôm nay học viên mở một stage trong plugin thì nhận một project mới + file `stages/stage-N.md` mở bằng Markdown preview của IntelliJ
(bảng bị vỡ: "CommandOutput DEPOSITNew balance…"); nút **Check Stage / Next Stage** nằm ở thanh công cụ chính, khó thấy; kết quả hiện bằng
notification. Mỗi stage là một project riêng nên code của stage trước không đi theo.

**Mục tiêu:** toàn bộ vòng lặp *đọc đề → chạy thử → nộp → mở khoá stage sau* diễn ra trong **một tool window** và **một project**,
không có file `.md` nào trong project.

**Giá trị cho người dùng**
- Học viên: không phải chuyển cửa sổ/tab; thấy kết quả và lỗi đầu tiên ngay cạnh đề; code Mini Bank được giữ qua các stage (đúng tinh thần dự án nhiều giai đoạn).
- Giáo viên: đề hiển thị đúng như trên web (cùng nguồn Markdown), không phải lo file đề trong project học viên bị lệch phiên bản.

**Chỉ số thành công**
- 100% stage mở/giải/qua stage được mà không rời IntelliJ (trừ khi bấm link bài học).
- Không còn file `stages/*.md` trong project mới tạo.
- Từ lúc bấm **Check** tới khi thấy kết quả ≤ thời gian chấm của server + 1 s (polling).

## 2. Quyết định đã chốt (phỏng vấn 2026-11-15)

| # | Câu hỏi | Quyết định |
|---|---------|-----------|
| D1 | Đề hiển thị ở đâu | Tool window **chia đôi**: cây study plan ở trên (thu gọn được), đề của stage đang chọn ở dưới |
| D2 | Nút chấm | **Run samples** (testcase công khai, `/run`) + **Check** (nộp chính thức, mọi testcase); kết quả **inline** dưới nút |
| D3 | Qua stage | Tick ✔, mở khoá stage sau, hiện nút **Next stage →**; Next stage chuyển đề sang stage sau **trong cùng project**, giữ code |
| D4 | File `.md` | **Ngừng ghi** `stages/stage-N.md`; đề chỉ ở tool window |
| D5 | File của stage sau | **Thêm file starter còn thiếu, không bao giờ ghi đè** file học viên đã có; báo "Đã thêm: X.java, Y.java" |
| D6 | Bài học (topics) của stage | Danh sách link (kèm tick đã học) — bấm mở bài học trên **web** |
| D7 | Đồng bộ tiến độ | Trạng thái stage luôn lấy từ server (giải trên web ⇒ plugin mở khoá sau refresh, và ngược lại) |
| D8 | Tự refresh | Sau mỗi Check, tự refresh cây/tiến độ |
| D9 | Phím tắt | Run samples `Ctrl+Alt+R`, Check `Ctrl+Alt+Enter` (đổi được trong Keymap) |
| D10 | Lịch sử | Hiện các lần nộp của stage này (verdict, thời gian) dưới kết quả |

## 3. Kiến trúc tóm tắt

```
ToolWindow "Mini LeetCode"  (OnePixelSplitter, dọc, tỉ lệ lưu theo project)
├─ Top:    Tree (StudyPlanTreeModel)  — giữ nguyên + icon 🔒 cho LOCKED
└─ Bottom: StagePanel (mới)
   ├─ Header: "Stage N · <title>" · badge độ khó · [▶ Run samples] [✔ Check] · [Next stage →] (chỉ khi DONE và có stage sau)
   ├─ ResultPanel: verdict · passed x/y · runtime · lỗi testcase đầu tiên (expected vs actual)
   ├─ Statement: Markdown → HTML (JCEF nếu có, fallback JEditorPane/JBHtmlPane) — cùng Markdown với web
   ├─ Topics: danh sách link `{serverUrl}/topics/{slug}?plan=…&stage=N` + tick DONE
   └─ History: 10 lần nộp gần nhất của stage (verdict · thời gian · Run/Check)
```

**Dữ liệu lấy từ API (không thêm bảng mới):**
- Đề: `GET /api/v1/problems/{id}` (`description` Markdown) — đã có trong PAT allowlist.
- Trạng thái stage + topics: `GET /api/v1/study-plans/{slug}` (`Stage.status`, `Stage.topics[].progress`) — đã có.
- Run samples: `POST /api/v1/problems/{id}/run` / `run-multipart` — **phải thêm vào PAT allowlist**.
- Check: `POST /api/v1/problems/{id}/submit-multipart` — đã có. Poll `GET /api/v1/submissions/{id}` — đã có.
- Lịch sử: `GET /api/v1/problems/{id}/submissions` — **phải thêm vào PAT allowlist** (chỉ trả bài nộp của chính user).
- Starter còn thiếu: `GET /api/v1/problems/{id}/starter-template` (zip) — đã có; plugin chỉ giải nén file chưa tồn tại.

**Project:** một project cho cả item (ví dụ `mini-bank-console`), metadata `.mlc/stage.json` lưu `{planSlug, itemId, currentStage}`.
Mở stage đầu = tạo project như hôm nay (bỏ ghi `.md`). Next stage = cập nhật `currentStage` + thêm file thiếu.

## 4. Functional Requirements (EARS)

### Hiển thị đề
- **FR-IP-01** — Khi học viên chọn một stage trong cây, plugin **shall** hiển thị tiêu đề, độ khó và đề bài (render từ Markdown của server) ở nửa dưới tool window trong ≤ 1 s sau khi API trả về.
- **FR-IP-02** — Plugin **shall** render bảng, khối code, code inline, danh sách và tiêu đề đúng như trang web (không dính chữ như "CommandOutput").
- **FR-IP-03** — Khi project đang mở thuộc một item lộ trình, plugin **shall** tự chọn và hiển thị stage hiện tại (`currentStage`) khi mở tool window.
- **FR-IP-04** — Plugin **shall not** ghi file `stages/stage-N.md` (hay bất kỳ file đề nào) vào project.
- **FR-IP-05** — Nếu stage ở trạng thái `LOCKED`, plugin **shall** hiển thị "Hoàn thành stage N-1 để mở khoá" thay cho đề và vô hiệu hoá Run samples / Check.
- **FR-IP-06** — Nếu stage đang bảo trì (`published = false`), plugin **shall** hiển thị "Stage đang được cập nhật" và vô hiệu hoá Run samples / Check.

### Run samples / Check
- **FR-IP-07** — Khi học viên bấm **Run samples** (hoặc `Ctrl+Alt+R`), plugin **shall** lưu mọi file đang mở, gửi các file `.java` của project tới `/run-multipart` và hiển thị kết quả chỉ trên testcase công khai.
- **FR-IP-08** — Khi học viên bấm **Check** (hoặc `Ctrl+Alt+Enter`), plugin **shall** lưu mọi file đang mở, nộp chính thức tới `/submit-multipart` và hiển thị verdict, số testcase pass / tổng, runtime và (nếu sai) testcase đầu tiên thất bại với output mong đợi và output thực tế.
- **FR-IP-09** — Trong khi đang chấm, plugin **shall** hiện trạng thái "Đang chấm…" và vô hiệu hoá Run samples / Check để tránh nộp trùng.
- **FR-IP-10** — Khi Check trả về `AC`, plugin **shall** đánh dấu stage ✔, tự refresh tiến độ từ server (D8) và hiện nút **Next stage →** nếu còn stage sau.
- **FR-IP-11** — Kết quả **Run samples** **shall not** đánh dấu stage hoàn thành (khớp với server: chỉ nộp chính thức mới tính).

### Next stage (cùng project)
- **FR-IP-12** — Khi học viên bấm **Next stage →**, plugin **shall** chuyển phần đề sang stage kế tiếp, cập nhật `.mlc/stage.json` và giữ nguyên mọi file hiện có.
- **FR-IP-13** — Khi chuyển stage, plugin **shall** tải starter của stage mới và chỉ tạo các file **chưa tồn tại** trong project, rồi thông báo danh sách file đã thêm (D5).
- **FR-IP-14** — Plugin **shall never** ghi đè hoặc xoá file học viên đã có khi chuyển stage.
- **FR-IP-15** — Nếu server trả `STAGE_LOCKED` khi chuyển/mở stage, plugin **shall** giữ stage hiện tại và hiển thị lý do.

### Bài học, đồng bộ, lịch sử
- **FR-IP-16** — Plugin **shall** liệt kê các bài học của stage kèm tick đã học; khi bấm một bài, plugin **shall** mở `{serverUrl}/topics/{slug}` trong trình duyệt mặc định (D6).
- **FR-IP-17** — Trạng thái `DONE` / `LOCKED` / `IN_PROGRESS` của stage **shall** luôn lấy từ server; plugin **shall** cập nhật khi bấm Refresh, sau mỗi Check, và khi tool window được mở lại (D7).
- **FR-IP-18** — Plugin **shall** hiển thị tối đa 10 lần nộp gần nhất của stage hiện tại (verdict, thời điểm, Run/Check) và cập nhật sau mỗi lần chấm (D10).

## 5. Non-Functional Requirements

| Mã | Yêu cầu |
|----|---------|
| NFR-IP-01 Hiệu năng | Mở/chọn stage hiển thị đề ≤ 1 s (sau API); UI không bao giờ bị khoá — mọi gọi mạng chạy nền (`Task.Backgroundable` / pooled thread), cập nhật UI trên EDT. |
| NFR-IP-02 Polling | Poll kết quả mỗi 1 s, tối đa 120 s; quá hạn hiện "Chấm quá lâu, xem lại sau" + link lịch sử. |
| NFR-IP-03 Bảo mật | PAT chỉ lưu trong PasswordSafe (như hôm nay). Allowlist PAT chỉ mở thêm **đúng** `POST /problems/*/run`, `POST /problems/*/run-multipart`, `GET /problems/*/submissions`; endpoint lịch sử chỉ trả bài nộp của chính chủ token. HTML render từ Markdown phải được sanitize (không chạy script, không tải tài nguyên ngoài trừ ảnh https). Link bài học chỉ mở URL cùng origin với `serverUrl`. |
| NFR-IP-04 An toàn dữ liệu | Không thao tác nào của plugin được ghi đè/xoá file người học (FR-IP-14); mọi ghi file qua `WriteCommandAction` (undo được). |
| NFR-IP-05 Tương thích | IntelliJ ≥ 253 (2025.3), cả bản không có JCEF (fallback renderer). Light/Dark theme đọc rõ. |
| NFR-IP-06 Truy cập | Mọi nút có tooltip + phím tắt hiển thị; kết quả đọc được bằng screen reader (accessible name). |
| NFR-IP-07 Kiểm thử | Unit test cho: render Markdown (bảng/khối code), chọn file thiếu (D5), máy trạng thái nút (LOCKED/judging/AC); test API client cho 3 endpoint mới (MockWebServer); backend test PAT allowlist. |

## 6. Acceptance Criteria

1. **Đề trong plugin** — *Given* học viên đã đăng nhập plugin và mở plan "OOP in Java", *When* chọn "Stage 1", *Then* nửa dưới tool window hiện đề Mini Bank 1/5 với bảng Command/Output hiển thị thành bảng 2 cột, và project không có thư mục `stages/`.
2. **Run samples** — *Given* code mẫu chưa sửa, *When* bấm Run samples, *Then* kết quả hiện "Wrong Answer · 0/2 · public tests only" kèm testcase #1 expected vs actual, và stage vẫn chưa ✔.
3. **Check qua bài** — *Given* code đúng cho stage 1, *When* bấm Check, *Then* trong ≤ thời gian chấm + 1 s hiện "Accepted · 3/3", Stage 1 có ✔, Stage 2 đổi từ 🔒 sang ○, và nút "Next stage →" xuất hiện.
4. **Next stage giữ code** — *Given* stage 1 đã AC và project có `Account.java` đã sửa, *When* bấm Next stage, *Then* đề đổi sang Stage 2, `Account.java` giữ nguyên nội dung, `SavingsAccount.java` và `CheckingAccount.java` được tạo, và hiện "Đã thêm: SavingsAccount.java, CheckingAccount.java".
5. **Stage khoá** — *Given* Stage 1 chưa AC, *When* chọn Stage 2 trong cây, *Then* hiện "Hoàn thành stage 1 để mở khoá", Run samples và Check bị vô hiệu hoá.
6. **Đồng bộ web** — *Given* học viên giải Stage 2 trên web, *When* bấm Refresh trong plugin, *Then* Stage 2 có ✔ và Stage 3 được mở khoá.
7. **Phím tắt** — *Given* đang ở một file Java của project lộ trình, *When* nhấn `Ctrl+Alt+Enter`, *Then* plugin thực hiện Check như bấm nút.
8. **Lịch sử** — *Given* đã nộp 2 lần (WA rồi AC), *When* xem stage, *Then* danh sách lịch sử hiện 2 dòng, mới nhất ở trên, có nhãn Run/Check.
9. **Bài học** — *Given* Stage 1 có 2 bài học, một đã học, *When* xem stage, *Then* thấy 2 link, một có tick; bấm link mở `{serverUrl}/topics/<slug>` trong trình duyệt.

## 7. Error Handling

| Tình huống | Phát hiện | Hành vi plugin |
|-----------|-----------|----------------|
| Chưa đăng nhập / PAT hết hạn hoặc bị thu hồi (401 `PAT_INVALID`) | HTTP 401 | Banner "Phiên hết hạn — tạo token mới" + nút mở Settings; giữ nguyên đề đã tải |
| Endpoint chưa cho PAT (403 `PAT_FORBIDDEN`) | HTTP 403 | "Thao tác này chỉ có trên web" + link mở web |
| Stage bị khoá (409 `STAGE_LOCKED`) | HTTP 409 | Hiện lý do từ server, khoá nút (FR-IP-05/15) |
| Stage đang bảo trì | `published = false` / 409 | "Stage đang được cập nhật, thử lại sau" |
| Không kết nối được server / sai `serverUrl` (vd. trỏ nhầm cổng 8080) | IOException / HTML thay vì JSON | "Không kết nối được <serverUrl> — kiểm tra Settings › Tools › Mini LeetCode" (nêu rõ URL) |
| Lỗi biên dịch | verdict `CE` | Hiện output biên dịch (rút gọn 30 dòng) trong ResultPanel |
| Chấm quá 120 s | Poll timeout | "Chấm quá lâu" + link lịch sử; mở lại nút |
| Payload quá lớn (413) | HTTP 413 | "Mã nguồn vượt giới hạn" + kích thước |
| Không có file `.java` nào | Kiểm tra trước khi gửi | "Không tìm thấy file Java trong project" , không gọi API |
| Starter tải lỗi khi Next stage | IOException | Vẫn chuyển đề; báo "Không tải được file mới, thử lại" + nút Retry; không đụng file hiện có |
| JCEF không có | `JBCefApp.isSupported() == false` | Dùng renderer Swing (đọc được, ít CSS hơn) |

## 8. Implementation TODO

### Slice A — Backend (nhỏ)
- [ ] Thêm `POST /api/v1/problems/*/run`, `POST /api/v1/problems/*/run-multipart`, `GET /api/v1/problems/*/submissions` vào `PAT_ALLOWLIST` (`JwtAuthenticationFilter`).
- [ ] Xác nhận `GET /problems/{id}/submissions` chỉ trả bài nộp của chính user (test).
- [ ] Test: PAT gọi được 3 endpoint mới; các endpoint khác vẫn 403 `PAT_FORBIDDEN`.

### Slice B — Plugin: StagePanel + đề
- [ ] `MiniLeetCodeToolWindowFactory`: `OnePixelSplitter` (tree trên, `StagePanel` dưới), lưu tỉ lệ.
- [ ] `StagePanel`: header, statement renderer (Markdown → HTML bằng thư viện Markdown sẵn có của nền tảng; JCEF + fallback), sanitize HTML.
- [ ] Chọn node stage ⇒ tải `ProblemDto` + hiển thị; trạng thái LOCKED / bảo trì.
- [ ] Bỏ ghi `stages/stage-N.md` trong luồng mở stage; bỏ `MarkdownOpener` khỏi luồng.
- [ ] Unit test render (bảng, khối code, code inline).

### Slice C — Plugin: Run samples / Check inline
- [ ] `ApiClient`: `runMultipart`, `listProblemSubmissions` (+ MockWebServer test).
- [ ] Nút Run samples / Check trong header + action có phím tắt (`Ctrl+Alt+R`, `Ctrl+Alt+Enter`) đăng ký trong `plugin.xml`.
- [ ] `ResultPanel`: verdict, x/y, runtime, testcase lỗi đầu tiên; trạng thái "Đang chấm…"; polling 1 s / 120 s.
- [ ] Sau Check: auto refresh tiến độ (D8); AC ⇒ ✔ + nút Next stage.

### Slice D — Plugin: Next stage cùng project
- [ ] `.mlc/stage.json` lưu `{planSlug, itemId, currentStage}`; mở tool window ⇒ chọn stage hiện tại.
- [ ] Next stage: tải starter zip, tạo file thiếu (không ghi đè) trong `WriteCommandAction`, thông báo danh sách file.
- [ ] Unit test chọn file thiếu (không ghi đè, đường dẫn con, file trùng tên khác hoa/thường trên Windows).

### Slice E — Bài học + lịch sử
- [ ] Danh sách topics của stage (tick DONE), mở `{serverUrl}/topics/{slug}` (chỉ cùng origin).
- [ ] History: 10 lần nộp gần nhất, cập nhật sau mỗi lần chấm.

### Slice F — Kiểm thử thủ công trong IntelliJ (cần Computer use) + tài liệu
- [ ] Chạy AC 1–9 trên sandbox `runIde` với stack local (`serverUrl` = địa chỉ web, ví dụ `http://localhost:3100`).
- [ ] Cập nhật `study-plan.spec.md` (PL-4, PL-6 bị thay thế), trang `/ide` của web và README plugin.

## 9. Out of Scope (v1)
- Hiển thị/giải bài học (lý thuyết, quiz) ngay trong plugin — chỉ có link (D6).
- Bài ngoài lộ trình (catalog `/problems`) trong plugin.
- Chạy code cục bộ trong IDE thay cho server (Run samples vẫn chấm trên server).
- So sánh/merge file khi starter stage sau khác file học viên (D5 chỉ thêm file thiếu).

## 10. Câu hỏi còn mở
- Có cần nút "Reset file về starter" cho từng file khi học viên muốn làm lại không? (mặc định: không, v2)
- Project cũ (mỗi stage một project, đã có `stages/*.md`) — có cần di trú sang mô hình một project không? (đề xuất: không, chỉ áp dụng cho stage mở mới)
