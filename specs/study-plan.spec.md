# Feature: Study Plan (Lộ trình học)

> **Trạng thái:** DRAFT — chờ phỏng vấn chốt quyết định. **Chưa code.**
> Sau khi có câu trả lời cho mục [Câu hỏi phỏng vấn](#câu-hỏi-phỏng-vấn), bảng "Quyết định" sẽ được chốt,
> FR/AC được sửa theo, và mới thêm mục *Implementation TODO*.

## Overview

**Study Plan** là một danh sách bài tập được sắp xếp có chủ đích (theo chủ đề / theo ngày) để học viên
đi từ dễ đến khó thay vì tự chọn bài lẻ trong `/problems`. Học viên tham gia một plan, làm từng bài,
và thấy tiến độ của mình trên plan đó.

Giá trị nghiệp vụ:
- **Học viên:** biết "hôm nay làm bài gì tiếp", có mục tiêu rõ ràng và cảm giác hoàn thành.
- **Giảng viên:** đóng gói ngân hàng đề thành lộ trình (vd. "DSA cơ bản", "OOP & Socket Java"), giao cho lớp và theo dõi.

## Hiện trạng có thể tái sử dụng (đã kiểm chứng trong code)

| Thành phần có sẵn | Vị trí | Dùng cho Study Plan |
|---|---|---|
| Đề có `difficulty`, `tags`, `problem_type` (`ALGORITHMIC` / `OOP_GRINDING`), `status` | `problems`, `problem_tags` (V1, V3, V4) | Chọn bài vào plan, nhóm theo chủ đề |
| Tiến độ từng bài `UserProgress` = `DONE` / `IN_PROGRESS` / `NEVER` | `ProblemService.resolveProgress`, `SubmissionRepository.findSolved…/findAttempted…` | Tính % hoàn thành plan — không cần lưu lại kết quả |
| Phân biệt Run và Submit (`is_quick_run`) | `submissions` (V6) | Run không được tính là "đã làm" |
| Vai trò `USER` / `INSTRUCTOR` / `ADMIN` | `users.role` | Ai được tạo / sửa plan |
| Vòng đời `DRAFT → REVIEWING → PUBLISHED → ARCHIVED` | `ProblemStatus` + `POST /problems/{id}/transitions` | Có thể áp dụng lại cho plan |
| 20 đề mẫu (12 thuật toán, 8 OOP/Socket) | `backend/tools/problems/problems.json` | Seed 1–2 plan mẫu |
| Migration tiếp theo | Flyway `V8__…` | Bảng plan mới |

**Chưa có:** khái niệm lớp học / nhóm, thông báo (email / push), lịch / deadline cho bài luyện tập, trang hồ sơ cá nhân.

**Phát hiện cần lưu ý:**
1. `findAttemptedProblemIdsByUserId` **không lọc** `quickRun = false` ⇒ chỉ bấm Run cũng làm bài thành `IN_PROGRESS`.
2. `findSolvedProblemIdsByUserId` **không lọc** `contest_id` ⇒ bài AC trong kỳ thi cũng tính `DONE` ở chế độ luyện tập.
3. Bài OOP có điểm thành phần (`score`); hiện "DONE" chỉ dựa vào verdict `AC`.

## Đề xuất mặc định (dùng nếu bạn trả lời "theo đề xuất")

Một MVP gọn, khớp hạ tầng hiện có:
- Giảng viên / Admin tạo plan; học viên chỉ tham gia (không tự tạo).
- Plan = nhiều **section** (theo chủ đề hoặc "Ngày 1, Ngày 2…"), mỗi section là danh sách bài có thứ tự.
- Học viên làm tự do, không khoá bài sau; plan chỉ gợi ý "bài tiếp theo".
- "Hoàn thành một bài" = có bài nộp chính thức `AC` (không tính Run); tính cả bài đã AC trước khi tham gia.
- Tiến độ tính trực tiếp từ `submissions`, chỉ lưu bản ghi tham gia (`enrollment`).
- Không có deadline, lớp học, thông báo ở MVP.

## Functional Requirements (nháp — sẽ chỉnh sau phỏng vấn)

### FR-SP-001: Danh sách plan
When người dùng mở trang Study Plans, the system shall hiển thị các plan `PUBLISHED` kèm tiêu đề, mô tả ngắn, số bài, độ khó, và % hoàn thành của chính người dùng (nếu đã tham gia).

### FR-SP-002: Chi tiết plan
When người dùng mở một plan, the system shall hiển thị các section theo thứ tự, mỗi bài kèm độ khó, loại đề và trạng thái `DONE` / `IN_PROGRESS` / `NEVER` của người dùng.

### FR-SP-003: Tham gia plan
When học viên đã đăng nhập bấm "Bắt đầu", the system shall tạo bản ghi tham gia (thời điểm bắt đầu) và đưa plan vào mục "Plan của tôi".

### FR-SP-004: Tính tiến độ
While học viên đang tham gia một plan, the system shall tính tiến độ = số bài `DONE` / tổng số bài trong plan, theo quy tắc hoàn thành đã chốt (Q-C1…Q-C3).

### FR-SP-005: Bài tiếp theo
While học viên đang tham gia một plan, the system shall chỉ ra bài đầu tiên chưa `DONE` theo thứ tự plan và cho phép mở thẳng workspace của bài đó.

### FR-SP-006: Ngữ cảnh plan trong workspace
While học viên mở một bài từ plan, the system shall hiển thị tên plan, vị trí bài (vd. "3/20") và nút sang bài tiếp theo.

### FR-SP-007: Tạo & sửa plan
Where người dùng có vai trò `INSTRUCTOR` hoặc `ADMIN`, the system shall cho phép tạo plan, thêm / xoá / sắp xếp section và bài (chỉ chọn bài `PUBLISHED`).

### FR-SP-008: Vòng đời plan
The system shall quản lý plan qua các trạng thái `DRAFT` → `PUBLISHED` → `ARCHIVED`; chỉ plan `PUBLISHED` hiển thị với học viên; plan `ARCHIVED` vẫn hiển thị với người đã tham gia.

### FR-SP-009: Bài bị gỡ khỏi ngân hàng đề
If một bài trong plan bị chuyển sang `ARCHIVED`, then the system shall loại bài đó khỏi tổng số bài khi tính tiến độ (chờ chốt Q-E2).

## Acceptance Criteria (nháp)

### AC-001: Tham gia và thấy tiến độ ban đầu
- **Given** học viên đã AC bài `two-sum` trước đây, plan "DSA cơ bản" có 10 bài gồm `two-sum`
- **When** học viên bấm "Bắt đầu"
- **Then** plan hiển thị tiến độ `1/10 (10%)` và "Bài tiếp theo" là bài chưa AC đầu tiên

### AC-002: Run không tính là đã làm
- **Given** học viên đang tham gia plan, bài `valid-parentheses` ở trạng thái `NEVER`
- **When** học viên chỉ bấm Run (không Submit)
- **Then** bài vẫn ở `NEVER` và tiến độ plan không đổi

### AC-003: Submit AC cập nhật tiến độ
- **Given** học viên đang ở bài 3/10 của plan
- **When** học viên Submit và nhận `AC`
- **Then** bài chuyển `DONE`, tiến độ tăng 1, nút "Bài tiếp theo" trỏ tới bài chưa `DONE` kế tiếp

### AC-004: Học viên không sửa được plan
- **Given** người dùng có vai trò `USER`
- **When** gọi API tạo hoặc sửa plan
- **Then** hệ thống trả `403`

### AC-005: Plan nháp bị ẩn
- **Given** một plan đang `DRAFT`
- **When** học viên mở danh sách plan hoặc truy cập trực tiếp URL của plan
- **Then** plan không xuất hiện và URL trả `404`

## Out of Scope (đề xuất, chờ xác nhận)
- Plan sinh tự động theo điểm yếu (gợi ý cá nhân hoá) — để v2.
- Thông báo email / push nhắc học.
- Nội dung lý thuyết dạng bài giảng / video.
- Chấm điểm, xếp hạng, chống gian lận trong plan (plan thuộc chế độ Luyện tập).

---

## Câu hỏi phỏng vấn

Trả lời theo mã câu (vd. `A1: b`, `B2: theo đề xuất`). Mỗi câu có **(Đề xuất)** để bạn chỉ cần xác nhận nếu đồng ý.
Các câu đánh dấu ⭐ quyết định phạm vi — cần trả lời trước.

### A. Mục tiêu & người dùng
- ⭐ **A1. Mục đích chính của study plan là gì?**
  a) Học viên tự học theo lộ trình có sẵn **(Đề xuất)** · b) Giảng viên giao bài cho lớp, có hạn nộp và theo dõi · c) Cả hai
- ⭐ **A2. Ai tạo plan?**
  a) Chỉ Instructor / Admin **(Đề xuất)** · b) Học viên cũng tự tạo plan riêng · c) Hệ thống tự sinh theo chủ đề / điểm yếu
- ⭐ **A3. Có cần khái niệm "lớp học / nhóm" không?** (hiện chưa có — sẽ thêm bảng, màn hình quản lý thành viên)
  a) Không, plan công khai cho mọi học viên **(Đề xuất)** · b) Có, plan gán cho lớp · c) Không cần lớp, nhưng plan riêng tư vào bằng mã mời
- **A4. Kết quả bạn mong muốn nhất là gì?** (vd. demo đồ án, dùng thật cho một lớp, tăng tỉ lệ học viên quay lại…) và **hạn chót** là khi nào?

### B. Cấu trúc plan
- ⭐ **B1. Plan được tổ chức thế nào?**
  a) Danh sách phẳng · b) Theo section chủ đề (Array → Hash Map → Graph…) **(Đề xuất)** · c) Theo ngày (Ngày 1…Ngày N, mỗi ngày vài bài)
- **B2. Có trộn bài thuật toán và bài OOP/Socket trong cùng plan?**
  a) Có **(Đề xuất)** · b) Không, mỗi plan một loại
- **B3. Thứ tự làm bài?**
  a) Tự do, chỉ gợi ý "bài tiếp theo" **(Đề xuất)** · b) Khoá bài sau đến khi AC bài trước · c) Khoá theo section
- **B4. Ngoài bài tập, plan có nội dung khác không?** (ghi chú Markdown đầu section, link tài liệu, gợi ý / lời giải)
  a) Chỉ mô tả ngắn cho plan và section **(Đề xuất)** · b) Có bài đọc lý thuyết riêng · c) Có lời giải mở khoá sau khi AC
- **B5. Một bài có được nằm trong nhiều plan không?** a) Có **(Đề xuất)** · b) Không

### C. Quy tắc hoàn thành & tiến độ
- ⭐ **C1. Thế nào là "đã hoàn thành" một bài?**
  a) Có bài nộp chính thức `AC` **(Đề xuất)** · b) Bài OOP: `score` ≥ ngưỡng (vd. 80%) · c) Chỉ cần đã nộp
- **C2. Bài đã AC trước khi tham gia plan có được tính?**
  a) Có **(Đề xuất)** · b) Không, chỉ tính bài nộp sau thời điểm bắt đầu plan
- **C3. Bài AC trong kỳ thi có tính vào tiến độ plan / trang luyện tập không?** (hiện code đang tính)
  a) Có, giữ như hiện tại · b) Không, chỉ tính bài nộp ngoài kỳ thi
- **C4. Chỉ bấm Run có được tính `IN_PROGRESS` không?** (hiện code đang tính — có thể là lỗi)
  a) Không, sửa luôn trong feature này **(Đề xuất)** · b) Giữ như hiện tại
- **C5. Học viên có được "làm lại từ đầu" (reset tiến độ plan) không?** a) Không **(Đề xuất)** · b) Có

### D. Thời gian, động lực
- **D1. Có lịch / hạn hoàn thành không?**
  a) Không **(Đề xuất)** · b) Gợi ý nhịp độ (vd. "2 bài/ngày") nhưng không bắt buộc · c) Hạn chót cứng do giảng viên đặt
- **D2. Có streak (chuỗi ngày học liên tiếp), huy hiệu hoàn thành, chứng nhận không?**
  a) Không ở MVP **(Đề xuất)** · b) Chỉ huy hiệu khi xong plan · c) Streak + huy hiệu
- **D3. Có cần nhắc nhở (email / thông báo trong app)?** a) Không **(Đề xuất)** · b) Thông báo trong app · c) Email

### E. Quản trị plan
- **E1. Vòng đời plan có cần bước duyệt như đề bài không?**
  a) `DRAFT → PUBLISHED → ARCHIVED`, không cần duyệt **(Đề xuất)** · b) Có bước `REVIEWING` do Admin duyệt
- **E2. Sửa plan khi đã có người học (thêm/bớt bài) thì xử lý sao?**
  a) Cho sửa, tiến độ tính lại theo danh sách mới **(Đề xuất)** · b) Khoá sửa sau khi có người tham gia · c) Tạo phiên bản mới
- **E3. Giảng viên có cần xem tiến độ của từng học viên trong plan không?**
  a) Không ở MVP · b) Bảng tổng hợp: học viên × bài, % hoàn thành **(Đề xuất nếu A1 = b/c)** · c) Có thêm xuất CSV
- **E4. Ai được sửa plan?** a) Người tạo + Admin **(Đề xuất)** · b) Mọi Instructor

### F. Giao diện
- **F1. Study Plan nằm ở đâu?**
  a) Trang mới `/study-plans` + mục trên header **(Đề xuất)** · b) Tab trong trang `/problems` · c) Thành trang chủ (hiện `/` chuyển về `/problems`)
- **F2. Trong workspace có hiện thanh ngữ cảnh plan ("DSA cơ bản · 3/20 · Bài tiếp theo →") không?** a) Có **(Đề xuất)** · b) Không
- **F3. Trang `/problems` có cần lọc "bài thuộc plan X" không?** a) Không **(Đề xuất)** · b) Có
- **F4. Bạn có mẫu giao diện tham khảo không?** (ảnh chụp, link sản phẩm thích)

### G. Phạm vi & nội dung khởi đầu
- **G1. Seed plan mẫu nào?**
  a) "DSA cơ bản" (12 bài thuật toán) + "OOP & Socket Java" (8 bài OOP) **(Đề xuất)** · b) Bạn tự định nghĩa
- **G2. Những gì chắc chắn KHÔNG làm ở đợt này?** (xác nhận mục Out of Scope ở trên, bổ sung nếu cần)
- **G3. Ưu tiên cắt lát (slice) khi thiếu thời gian?**
  a) Backend + trang xem plan cho học viên trước, màn hình tạo plan sau (seed bằng SQL) **(Đề xuất)** · b) Làm đủ cả màn hình giảng viên ngay
- **G4. Yêu cầu kiểm thử?** a) Unit + integration backend, E2E Playwright cho luồng tham gia → làm bài → tiến độ tăng **(Đề xuất)** · b) Chỉ backend
