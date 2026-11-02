# ĐẶC TẢ TÍNH NĂNG: NỘP BÀI ĐA HÌNH THỨC & STARTER SCAFFOLDING (HYBRID SUBMISSION SPECIFICATION)
## PHÂN HỆ OOP GRINDING — MINI LEETCODE PLATFORM

* **Mã đặc tả:** `SPEC-OOPG-SUBMISSION-001`
* **Phiên bản:** 1.0.0
* **Phương pháp luận:** Áp dụng chuẩn `feature-forge` (EARS Requirements & Gherkin Scenarios) kết hợp `grill-me` (giải quyết nhánh quyết định).

---

## 1. TỔNG QUAN & GIÁ TRỊ NGHIỆP VỤ (EXECUTIVE SUMMARY & USER VALUE)

### 1.1. Bối cảnh
Trước đây, các hệ thống chấm bài tập lập trình hướng đối tượng thường chỉ ép buộc sinh viên:
* Hoặc chỉ nộp 1 file nén `.zip` $\to$ Bất tiện khi sinh viên chỉ muốn sửa 1 dòng code nhỏ hoặc code trực tiếp trên trình duyệt.
* Hoặc chỉ cho 1 khung text duy nhất $\to$ Khó viết các dự án có nhiều class/package, dễ dồn code spaghetti vào 1 file.

### 1.2. Giải pháp Hybrid Linh hoạt
Hệ thống **Mini LeetCode — OOP Grinding** thiết lập cơ chế **Nộp bài Đa hình thức (Hybrid Multi-Channel Ingestion)**:
1. **Soạn thảo trực tiếp trên Trình duyệt:** Cung cấp trình biên tập đa tab (Multi-tab/Tree Monaco Editor) nạp sẵn cây thư mục mẫu (Starter Scaffolding).
2. **Nộp file từ Máy tính:** Cho phép kéo thả file `.zip` hoặc chọn đồng thời nhiều file `.java`.
3. **Phát triển Ngoại tuyến (Offline-First):** Cung cấp nút `Download Starter Project (.zip)` để mở bằng IntelliJ IDEA / VS Code.
4. **Phân cấp Kiểm thử:** Tính năng `Run Code` chạy kiểm tra nhanh cấu trúc OOP & Test mẫu; tính năng `Submit` thực thi toàn trình (OOP, Full Unit Tests, Socket Harness).

---

## 2. ĐẶC TẢ YÊU CẦU CHỨC NĂNG CHUẨN EARS (FUNCTIONAL REQUIREMENTS)

### 2.1. Tiếp nhận Mã nguồn Đa kênh (Multi-Channel Ingestion)
* **REQ-SUB-001 (Unified JSON Submission):**
  * *When* sinh viên gửi bài nộp từ trình soạn thảo Web đa tab qua payload JSON chứa danh sách `files: [{"path": string, "content": string}]`, *the system shall* xác thực dữ liệu và đóng gói thành file `.zip` lưu vào MinIO S3 bucket `submissions`.
* **REQ-SUB-002 (Multipart File Upload):**
  * *When* sinh viên tải lên trực tiếp file nén `.zip` hoặc mảng file `.java` qua giao diện tải file, *the system shall* lưu trữ file vào MinIO S3 và tạo bản ghi bài nộp trạng thái `PENDING`.
* **REQ-SUB-003 (Delimited Single-Editor Compatibility):**
  * *Where* sinh viên sử dụng trình soạn thảo đơn giản với cú pháp đánh dấu `// === FILE: <filename> ===`, *the system shall* tự động phân tách nội dung thành các file độc lập trước khi biên dịch.

### 2.2. Khởi tạo Dự án Mẫu (Starter Scaffolding)
* **REQ-SCAF-001 (Template Scaffolding Delivery):**
  * *When* sinh viên truy cập một bài tập kiểu `OOP_GRINDING`, *the system shall* nạp cấu hình `starterTemplates` của bài tập và hiển thị cây thư mục mẫu (File Tree & Tabs) trên Monaco Editor.
* **REQ-SCAF-002 (Download Starter ZIP):**
  * *When* sinh viên nhấn nút `Download Starter Template`, *the system shall* sinh ra một file `.zip` chứa toàn bộ cấu trúc thư mục, file mã nguồn mẫu và file `README.md` hướng dẫn bài tập.

### 2.3. Phân cấp Chạy Thử & Chấm Chính Thức (Run vs Submit)
* **REQ-EXEC-001 (Fast Public Run Code):**
  * *When* sinh viên nhấn `Run Code`, *the system shall* chỉ biên dịch và kiểm tra các ràng buộc OOP công khai (`OopConstraintRule`) cùng các bài kiểm thử đơn vị mẫu (Sample Unit Tests), không kích hoạt các bài test Socket tải nặng.
* **REQ-EXEC-002 (Full Evaluation on Submit):**
  * *When* sinh viên nhấn `Submit`, *the system shall* đẩy tác vụ vào Redis Stream để thực thi toàn bộ chu trình 4 phase (Compile, OOP Reflection, Full Unit Test Suite, Ephemeral Socket Harness) và cập nhật điểm số vào bảng xếp hạng.

---

## 3. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS)

* **NFR-PERF-001 (Fast Run Latency):** Phản hồi của tính năng `Run Code` phải trả về cho người dùng trong vòng dưới $3.0\text{s}$.
* **NFR-SEC-001 (Path Traversal Rejection):** Hệ thống từ chối mọi bài nộp (cả file zip lẫn JSON bundle) chứa đường dẫn có ký tự `../` hoặc byte rỗng (`\0`), phát ra mã lỗi `400 Bad Request`.
* **NFR-SIZE-001 (Storage Limit):** Tổng dung lượng các file mã nguồn của một bài nộp không được vượt quá $20\text{MB}$.

---

## 4. TIÊU CHÍ NGHIỆM THU CHUẨN GHERKIN (ACCEPTANCE CRITERIA)

### Kịch bản 1: Sinh viên nộp bài qua Web Multi-tab Editor
```gherkin
Given sinh viên đang mở bài tập "Shape Hierarchy" trên giao diện Web
And sinh viên đã chỉnh sửa nội dung trên 2 tab: "Shape.java" và "Circle.java"
When sinh viên nhấn nút "Submit"
Then trình duyệt gửi payload JSON chứa mảng `files` đến `/api/v1/submissions`
And Web API Gateway đóng gói thành công file `submission-123.zip` vào MinIO S3
And trạng thái bài nộp được chuyển thành `PENDING` và đẩy vào Redis Stream
```

### Kịch bản 2: Sinh viên tải starter template về máy, code trên IDE rồi nộp file ZIP
```gherkin
Given sinh viên nhấn nút "Download Starter Template (.zip)" tại bài "TCP Echo Server"
When hệ thống tải về file "tcp-echo-server-starter.zip"
And sinh viên giải nén, code hoàn thiện trên IntelliJ IDEA và nén lại thành "my-solution.zip"
And sinh viên kéo thả "my-solution.zip" vào khung Upload File trên Web
Then hệ thống chấp nhận file, quét kiểm tra an toàn Zip Slip
And kích hoạt toàn bộ quy trình chấm điểm 4 phase thành công
```

### Kịch bản 3: Chạy thử nhanh với tính năng "Run Code"
```gherkin
Given sinh viên đã viết xong class "Shape.java" nhưng chưa hoàn thành lớp "Circle.java"
When sinh viên nhấn nút "Run Code"
Then hệ thống thực thi kiểm tra nhanh cấu trúc OOP trong vòng 1.5 giây
And hệ thống hiển thị kết quả: "Shape: Abstract class hợp lệ", "Circle: Thiếu method calculateArea()"
And không tốn tài nguyên chạy container Socket
And lần chạy này không ghi nhận điểm vào lịch sử thi chính thức
```

---

## 5. MA TRẬN XỬ LÝ LỖI (ERROR HANDLING MATRIX)

| Tình huống lỗi | Nguyên nhân | Phản hồi HTTP / Trạng thái | Thông điệp hiển thị cho Sinh viên |
| :--- | :--- | :---: | :--- |
| `EMPTY_SUBMISSION` | Không có file nào được gửi hoặc file zip rỗng | `400 Bad Request` | *"Bài nộp không chứa tệp mã nguồn hợp lệ."* |
| `PATH_TRAVERSAL` | File zip hoặc JSON chứa đường dẫn `../../` | `400 Bad Request` | *"Tên tệp không hợp lệ hoặc vi phạm quy tắc bảo mật thư mục."* |
| `PAYLOAD_TOO_LARGE` | Dung lượng mã nguồn vượt quá 20MB | `413 Payload Too Large` | *"Kích thước bài nộp vượt quá giới hạn cho phép (tối đa 20MB)."* |
| `COMPILE_ERROR_ON_RUN` | Lỗi cú pháp Java khi bấm `Run Code` | `200 OK` (Result payload) | Hiển thị chi tiết dòng lỗi và thông điệp từ `javac` trên console log. |

---

## 6. KẾ HOẠCH TRIỂN KHAI (TODO CHECKLIST)

### Frontend (Next.js 14 / Monaco Editor)
- [ ] Bổ sung thanh Tab Bar & File Tree bên trái khung Monaco Editor cho phép chuyển đổi giữa các file trong project.
- [ ] Bổ sung nút `+ New File` và nút `Upload Files (.zip / .java)`.
- [ ] Bổ sung nút `Download Starter (.zip)` tải cấu trúc bài mẫu.
- [ ] Tích hợp 2 nút riêng biệt: `Run Code` (chạy thử nhanh) và `Submit` (chấm chính thức).

### Backend (Web API Gateway & Judge)
- [ ] Cập nhật API `POST /api/v1/submissions` hỗ trợ tiếp nhận cả JSON `files: [...]` và Multipart file zip.
- [ ] Bổ sung endpoint `GET /api/v1/problems/{id}/starter-template` để sinh file zip mẫu cho sinh viên.
- [ ] Bổ sung cờ `isQuickRun: boolean` trong message gửi sang Redis Queue để Worker chỉ chạy Phase 1 + Phase 2 khi nhấn `Run Code`.
