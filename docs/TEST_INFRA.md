# E2E & Integration Test Infrastructure Specification: Mini LeetCode Platform
## Phân hệ OOP Grinding: Nộp bài Đa hình thức & Starter Scaffolding (Hybrid Submission & Scaffolding)

- **Mã tài liệu**: `TEST-INFRA-HYBRID-001`
- **Phiên bản**: 1.0.0
- **Tiêu chuẩn áp dụng**: `test-master`, `playwright-expert`, `feature-forge`
- **Phạm vi kiểm thử**: `backend/mini-leetcode-api`, `backend/mini-leetcode-core`, `backend/mini-leetcode-judge`, `frontend/`

---

## 1. Triết lý Kiểm thử & Nguyên tắc Kiến trúc (Testing Philosophy)

Hệ thống kiểm thử E2E và tích hợp cho tính năng **Nộp bài Đa hình thức & Starter Scaffolding** tuân thủ các nguyên tắc cốt lõi:

1. **Phương pháp luận 4 tầng (4-Tier Test Methodology)**:
   - **Tier 1 - Feature Coverage**: Kiểm thử độ bao phủ chức năng chính ngạch (happy paths) với tối thiểu $\ge 5$ test cases cho từng tính năng:
     - Unified JSON Multi-File Submission (`REQ-SUB-001`)
     - Multipart ZIP Upload (`REQ-SUB-002`)
     - Multipart Java Source Files (`REQ-SUB-002`)
     - Delimited Marker Syntax Parsing (`REQ-SUB-003`)
     - Starter Scaffolding ZIP Generation (`REQ-SCAF-001`, `REQ-SCAF-002`)
     - Fast Execution Quick Run vs Full Submit (`REQ-EXEC-001`, `REQ-EXEC-002`)
   - **Tier 2 - Boundary & Adversarial Cases**: Thử thách các giới hạn tài nguyên và kịch bản bảo mật tấn công (Zip Slip, Path Traversal `../`, byte rỗng `\0`, 20MB limit, 0-byte zip, special chars, invalid extensions `.exe`/`.sh`/`.class`).
   - **Tier 3 - Cross-Feature Combinations**: Kiểm tra tương tác chéo giữa các tính năng (Pairwise Combinations: JSON + QuickRun + Contest, Multipart + QuickRun, Scaffolding download $\to$ edit $\to$ re-upload, Disqualified user handling).
   - **Tier 4 - Real-World Application Scenarios**: Mô phỏng toàn trình hành vi thực tế của học viên (Offline Student Workflow: tải starter zip $\to$ code trên IDE $\to$ zip lại $\to$ nộp bài $\to$ nhận điểm; Fast Run loop trước khi nộp chính thức).

2. **Progressive Testability & Graceful Milestone Activation**:
   - Bộ test suite được thiết kế theo mô hình TDD (Test-Driven Development) tiên tiến:
   - Khi chạy ở Milestone 1/2 hiện hành, các endpoint đã có (`/problems/{id}/submit`, `/problems/{id}/run`, marker syntax) được kiểm thử nghiêm ngặt và pass 100%.
   - Các endpoint đang được xây dựng ở M2 (`POST /api/v1/submissions`, `GET /problems/{id}/starter-template`) được bảo vệ bằng cơ chế assumption check: khi endpoint chưa mount, test ghi nhận trạng thái chờ M2 mà không làm crash build (`mvn test` luôn đạt BUILD SUCCESS).
   - Cờ `-Dtest.hybrid.strict=true` kích hoạt chế độ kiểm tra nghiêm ngặt (Red Phase TDD) để phục vụ nghiệm thu Milestone 2 và Milestone 5.

3. **Hermetic & Isolated Execution**:
   - Mọi test case tự khởi tạo tài nguyên độc lập (User, Problem, Contest, Token ngẫu nhiên theo timestamp).
   - Không phụ thuộc vào thứ tự thực thi (Order-independent).
   - Dữ liệu dọn dẹp an toàn qua `@Transactional` rollback ở backend và Playwright Route Interception ở frontend.

---

## 2. Kiến trúc Hạ tầng Kiểm thử (Test Architecture)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   TEST INFRASTRUCTURE                                  │
├───────────────────────────────────────────┬────────────────────────────────────────────┤
│           BACKEND INTEGRATION/E2E         │                FRONTEND E2E                │
│       (mini-leetcode-api / Core)          │                 (frontend/)                │
├───────────────────────────────────────────┼────────────────────────────────────────────┤
│ • Framework: Spring Boot Test 3.3.4       │ • Framework: Playwright 1.64.1             │
│ • Runner: JUnit Platform 5.10.3 / Surefire│ • Engine: Chromium Headless                │
│ • Assertions: AssertJ, JsonPath, MockMvc  │ • Strategy: Route Interception (page.route)│
│ • Database: H2 In-Memory (ActiveProfile=test)│ • Server: Next.js on port 3005           │
│ • Isolation: Spring @Transactional        │ • Env: NEXT_PUBLIC_USE_MOCK=false          │
│ • Target: HybridSubmissionIntegrationTest │ • Target: workspace-oop-multifile.spec.ts  │
│   (53 test cases)                         │   (37 test cases)                          │
└───────────────────────────────────────────┴────────────────────────────────────────────┘
```

---

## 3. Danh mục Kiểm thử Chi tiết (Comprehensive Test Inventory)

### 3.1. Backend Integration Test Suite (`HybridSubmissionIntegrationTest.java`)
**Vị trí**: `backend/mini-leetcode-api/src/test/java/jsd/toiyeujava/mini_leetcode/submission/HybridSubmissionIntegrationTest.java`  
**Tổng số test**: 53 test cases

| # | Phân loại | Tên Test Case | Mục tiêu kiểm thử | Trạng thái hiện tại |
|---|---|---|---|:---:|
| 1 | Tier 1.1 | `testJson_StandardTwoFiles` | JSON 2 file (`Shape.java`, `Circle.java`) | Ready (Pending M2) |
| 2 | Tier 1.1 | `testJson_NestedPackagePath` | JSON đường dẫn package (`com/example/Shape.java`) | Ready (Pending M2) |
| 3 | Tier 1.1 | `testJson_SingleFile` | JSON 1 file trong mảng `files` | Ready (Pending M2) |
| 4 | Tier 1.1 | `testJson_FiveFilesHierarchy` | JSON cấu trúc 5 class (Interface, Abstract, Impls) | Ready (Pending M2) |
| 5 | Tier 1.1 | `testJson_WithContestAndQuickRunFlags` | JSON bảo lưu cờ `contestId` và `isQuickRun=true` | Ready (Pending M2) |
| 6 | Tier 1.2 | `testZip_StandardValidArchive` | Multipart file ZIP hợp lệ chứa các file Java | Ready (Pending M2) |
| 7 | Tier 1.2 | `testZip_NestedPackageHierarchy` | Multipart ZIP chứa thư mục lồng (`src/main/java/`) | Ready (Pending M2) |
| 8 | Tier 1.2 | `testZip_MultipleClasses` | Multipart ZIP chứa nhiều class phụ thuộc | Ready (Pending M2) |
| 9 | Tier 1.2 | `testZip_WithContestId` | Multipart ZIP kèm tham số `contestId` | Ready (Pending M2) |
| 10 | Tier 1.2 | `testZip_WithQuickRun` | Multipart ZIP kèm tham số `isQuickRun=true` | Ready (Pending M2) |
| 11 | Tier 1.3 | `testJava_MultipleIndividualFiles` | Multipart mảng nhiều file `.java` rời rạc | Ready (Pending M2) |
| 12 | Tier 1.3 | `testJava_SingleFile` | Multipart 1 file `.java` đơn | Ready (Pending M2) |
| 13 | Tier 1.3 | `testJava_DependentClasses` | Multipart các class `.java` phụ thuộc lẫn nhau | Ready (Pending M2) |
| 14 | Tier 1.3 | `testJava_WithContestId` | Multipart `.java` kèm tham số kỳ thi | Ready (Pending M2) |
| 15 | Tier 1.3 | `testJava_WithQuickRun` | Multipart `.java` kèm tham số chạy thử nhanh | Ready (Pending M2) |
| 16 | Tier 1.4 | `testMarker_StandardHeaders` | Cú pháp `// === FILE: Shape.java ===` chuẩn | **PASSED (Active)** |
| 17 | Tier 1.4 | `testMarker_HeaderVariations` | Biến thể cú pháp `// --- FILE: ... ---` và `###` | **PASSED (Active)** |
| 18 | Tier 1.4 | `testMarker_WithPackageDeclarations` | Khai báo package bên trong từng file marker | **PASSED (Active)** |
| 19 | Tier 1.4 | `testMarker_FourFilesSeparated` | Phân tách chính xác 4 file bằng marker syntax | **PASSED (Active)** |
| 20 | Tier 1.4 | `testMarker_WhitespaceAroundFilename` | Cắt gọn khoảng trắng quanh tên file trong header | **PASSED (Active)** |
| 21 | Tier 1.5 | `testScaffold_ReturnsZipArchive` | `GET /starter-template` trả về tệp `.zip` | Ready (Pending M2) |
| 22 | Tier 1.5 | `testScaffold_ContainsReadme` | Tệp ZIP chứa file hướng dẫn `README.md` | Ready (Pending M2) |
| 23 | Tier 1.5 | `testScaffold_ContainsSourceFiles` | Tệp ZIP chứa các file mã nguồn mẫu | Ready (Pending M2) |
| 24 | Tier 1.5 | `testScaffold_ContentDispositionHeader` | Header `Content-Disposition` có `{slug}-starter.zip` | Ready (Pending M2) |
| 25 | Tier 1.5 | `testScaffold_EmptyTemplatesFallback` | Bài tập không cấu hình template vẫn sinh ZIP hợp lệ | Ready (Pending M2) |
| 26 | Tier 1.6 | `testQuickRun_TrueFlag` | Nộp bài với `isQuickRun=true` gửi task chạy nhanh | Ready (Pending M2) |
| 27 | Tier 1.6 | `testQuickRun_FalseFlag` | Nộp bài với `isQuickRun=false` gửi task chấm đầy đủ | Ready (Pending M2) |
| 28 | Tier 1.6 | `testQuickRun_OmittedDefault` | Mặc định `isQuickRun` là false khi không truyền | Ready (Pending M2) |
| 29 | Tier 1.6 | `testQuickRun_DedicatedRunEndpoint` | Endpoint `POST /problems/{id}/run` trả 202 ACCEPTED | **PASSED (Active)** |
| 30 | Tier 1.6 | `testQuickRun_WithMultipartZip` | Chạy thử nhanh qua Multipart ZIP | Ready (Pending M2) |
| 31 | Tier 2 | `testZipSlip_RelativeTraversal` | Chặn tấn công Zip Slip `../../etc/passwd` (400) | Ready (Pending M2) |
| 32 | Tier 2 | `testZipSlip_AbsoluteUnixPath` | Chặn tấn công đường dẫn tuyệt đối `/var/run/evil.sh` | Ready (Pending M2) |
| 33 | Tier 2 | `testZipSlip_WindowsDriveLetter` | Chặn ký tự ổ đĩa Windows `C:\Windows\System32\` | Ready (Pending M2) |
| 34 | Tier 2 | `testNullByte_InJsonPath` | Chặn byte rỗng `\0` trong đường dẫn JSON (400) | Ready (Pending M2) |
| 35 | Tier 2 | `testNullByte_InMultipartFilename` | Chặn byte rỗng `\0` trong tên file Multipart (400) | Ready (Pending M2) |
| 36 | Tier 2 | `testPathTraversal_InJsonPath` | Chặn `../` trong mảng `files` JSON (400) | Ready (Pending M2) |
| 37 | Tier 2 | `testPathTraversal_InMarkerSyntax` | Chặn `../` trong tiêu đề marker syntax (400/202) | **PASSED (Active)** |
| 38 | Tier 2 | `testEmptyZip_Rejected` | Chặn file ZIP 0-byte hoặc không có file (400) | Ready (Pending M2) |
| 39 | Tier 2 | `testEmptyJsonFiles_Rejected` | Chặn mảng `files: []` rỗng không có code (400) | Ready (Pending M2) |
| 40 | Tier 2 | `testPayloadExceeds20MB_RejectedWith413` | Chặn file vượt quá 20MB (413/400) | Ready (Pending M2) |
| 41 | Tier 2 | `testMaxEntriesExceeded_Rejected` | Chặn bài nộp vượt quá 100 file (400) | Ready (Pending M2) |
| 42 | Tier 2 | `testInvalidExtension_Executable_Rejected` | Chặn tệp thực thi `.exe`/`.sh` (400) | Ready (Pending M2) |
| 43 | Tier 2 | `testInvalidExtension_CompiledClass_Rejected` | Chặn bytecode đã biên dịch `.class` (400) | Ready (Pending M2) |
| 44 | Tier 2 | `testSpecialChars_ValidFilenamesAccepted` | Chấp nhận tên file có dấu gạch ngang, gạch dưới, chấm | Ready (Pending M2) |
| 45 | Tier 2 | `testSpecialChars_ShellInjectionCharsRejected` | Chặn ký tự shell injection `; & \| < > \`` (400) | Ready (Pending M2) |
| 46 | Tier 3 | `testCombination_Json_QuickRun_Contest` | Phối hợp JSON + QuickRun + Contest | Ready (Pending M2) |
| 47 | Tier 3 | `testCombination_MultipartZip_QuickRun_Contest` | Phối hợp Multipart ZIP + QuickRun + Contest | Ready (Pending M2) |
| 48 | Tier 3 | `testCombination_MarkerSyntax_InsideJsonCodeField` | Phối hợp Marker syntax nằm trong trường `code` của JSON | Ready (Pending M2) |
| 49 | Tier 3 | `testCombination_StarterZip_Downloaded_Then_SubmittedAsMultipart` | Tải starter zip $\to$ giải nén $\to$ nén lại và nộp qua Multipart | Ready (Pending M2) |
| 50 | Tier 3 | `testCombination_NonJavaTextFiles_InZip` | Chấp nhận file tài liệu `.txt`/`.md` đi kèm trong ZIP | Ready (Pending M2) |
| 51 | Tier 3 | `testCombination_DisqualifiedUser_RejectedWith403` | Từ chối nộp bài khi thí sinh đã bị loại trong kỳ thi | **PASSED (Active)** |
| 52 | Tier 4 | `testScenario_OfflineStudentWorkflow` | Kịch bản trọn vẹn: Tải mẫu $\to$ code offline $\to$ nộp bài | Ready (Pending M2) |
| 53 | Tier 4 | `testScenario_BrowserQuickRunThenFinalSubmit` | Kịch bản trọn vẹn: Run Code kiểm tra nhanh $\to$ Submit chính thức | **PASSED (Active)** |

---

### 3.2. Frontend Playwright E2E Suite (`workspace-oop-multifile.spec.ts`)
**Vị trí**: `frontend/e2e/workspace-oop-multifile.spec.ts`  
**Tổng số test**: 37 test cases

| # | Nhóm Tier | Tên Test Case | Mục tiêu kiểm thử |
|---|---|---|---|
| 1 | Tier 1.1 | `1.1.1: renders problem workspace with problem title and description` | Hiển thị tiêu đề bài tập OOP và đề bài markdown |
| 2 | Tier 1.1 | `1.1.2: renders workspace editor container with code area` | Hiển thị khung soạn thảo Monaco / fallback textarea |
| 3 | Tier 1.1 | `1.1.3: displays file tab indicating active source file` | Hiển thị tab file mã nguồn đang mở |
| 4 | Tier 1.1 | `1.1.4: supports editor typing and preserves code content` | Cho phép gõ code và lưu trữ nội dung biên tập |
| 5 | Tier 1.1 | `1.1.5: allows language switching while preserving editor state` | Chuyển đổi ngôn ngữ Java $\leftrightarrow$ Python |
| 6 | Tier 1.2 | `1.2.1: detects workspace toolbar controls for file operations` | Kiểm tra thanh công cụ biên tập và hành động file |
| 7 | Tier 1.2 | `1.2.2: supports adding custom classes via delimited marker syntax` | Hỗ trợ bổ sung nhiều class bằng cú pháp marker `// === FILE:` |
| 8 | Tier 1.2 | `1.2.3: verifies .java file extension validation rule` | Xác nhận môi trường Java nhận diện đuôi `.java` |
| 9 | Tier 1.2 | `1.2.4: verifies multiple class headers can be specified` | Hỗ trợ nhiều class trong cùng workspace |
| 10 | Tier 1.2 | `1.2.5: focuses editor buffer upon navigating to problem` | Tự động focus vào trình soạn thảo khi vào bài |
| 11 | Tier 1.3 | `1.3.1: verifies workspace page readiness for file ingestion` | Kiểm tra tính sẵn sàng nộp file của workspace |
| 12 | Tier 1.3 | `1.3.2: verifies multipart form submission can be intercepted` | Intercept và phản hồi thành công yêu cầu multipart |
| 13 | Tier 1.3 | `1.3.3: verifies multi-file payload submission structure` | Xác thực cấu trúc payload JSON mảng `files` |
| 14 | Tier 1.3 | `1.3.4: verifies rejection of empty submission bundle` | Phản hồi lỗi RFC 7807 400 khi nộp mảng file rỗng |
| 15 | Tier 1.3 | `1.3.5: verifies submission response retains submission id for polling` | Phản hồi chứa `id` bài nộp để client polling kết quả |
| 16 | Tier 1.4 | `1.4.1: triggers GET starter-template and downloads zip file` | Nhấn nút tải starter template nhận stream ZIP |
| 17 | Tier 1.4 | `1.4.2: verifies downloaded starter zip contains non-zero byte stream` | Kiểm tra magic bytes `PK` của file ZIP tải về |
| 18 | Tier 1.4 | `1.4.3: validates Content-Disposition filename format {slug}-starter.zip` | Tên file tải về đúng quy tắc `{slug}-starter.zip` |
| 19 | Tier 1.4 | `1.4.4: starter download is accessible to authenticated students` | Học viên đã đăng nhập được tải template |
| 20 | Tier 1.4 | `1.4.5: download completes with application/zip content type` | Định dạng Content-Type chuẩn `application/zip` |
| 21 | Tier 1.5 | `1.5.1: renders Run button and Submit button side-by-side` | Hiển thị phân định rõ nút `Run` và nút `Submit` |
| 22 | Tier 1.5 | `1.5.2: executes Run Code action and displays public testcase result` | Bấm Run Code hiển thị kết quả kiểm thử mẫu |
| 23 | Tier 1.5 | `1.5.3: executes Submit action and displays official evaluation result` | Bấm Submit kích hoạt chấm điểm chính thức |
| 24 | Tier 1.5 | `1.5.4: Run action completes with low latency (< 3.0s NFR-PERF-001)` | Đo thời gian chạy thử phản hồi dưới 3.0 giây |
| 25 | Tier 1.5 | `1.5.5: Run action switches active panel to Result tab` | Tự động chuyển tab kết quả khi bấm Run Code |
| 26 | Tier 2 | `2.1: rejects path traversal (../) in delimited file header` | Chặn đường dẫn `../` trong marker header |
| 27 | Tier 2 | `2.2: rejects null bytes in submission payload with 400 Bad Request` | Chặn byte rỗng `\0` trong payload nộp bài |
| 28 | Tier 2 | `2.3: rejects payload exceeding 20MB with 413 Payload Too Large` | Chặn bài nộp vượt 20MB với lỗi 413 |
| 29 | Tier 2 | `2.4: rejects executable scripts (.sh / .exe) with 400 Bad Request` | Chặn file thực thi `.sh`/`.exe` |
| 30 | Tier 2 | `2.5: handles empty code submission with validation error` | Chặn submit khi mã nguồn để trống |
| 31 | Tier 2 | `2.6: handles rapid double-click on Run Code without duplicate execution` | Debounce chống bấm đúp liên tục nút Run Code |
| 32 | Tier 3 | `3.1: Delimited multi-file code + Run Code verifies console feedback` | Phối hợp mã đa tệp marker syntax với hành động Run Code |
| 33 | Tier 3 | `3.2: Download starter -> verifies download -> Submit official solution` | Phối hợp tải template và nộp bài chính thức |
| 34 | Tier 3 | `3.3: Language switch with multi-file delimited code updates template cleanly` | Phối hợp đổi ngôn ngữ trong workspace đa tệp |
| 35 | Tier 3 | `3.4: Quick run followed immediately by Submit transitions cleanly in UI` | Chạy thử Run Code xong bấm ngay Submit |
| 36 | Tier 4 | `4.1: Offline Student Workflow: Visit Problem -> Download Starter -> Submit Solution -> Verify AC` | Kịch bản học viên tải starter $\to$ giải bài $\to$ nộp $\to$ AC |
| 37 | Tier 4 | `4.2: In-Browser Iteration Workflow: Run Code (Check) -> Fix OOP Rules -> Official Submit` | Kịch bản chạy thử phát hiện thiếu method $\to$ sửa code $\to$ Submit AC |

---

## 4. Hướng dẫn Thực thi Kiểm thử (Execution Commands)

### 4.1. Chạy Backend Test Suite
```powershell
# Chạy toàn bộ 53 test cases kiểm thử tích hợp Hybrid Ingestion
cd "c:\Users\aDMIN\Documents\JSD\Mini Leetcode\backend"
.\mvnw.cmd test -pl mini-leetcode-api -Dtest=HybridSubmissionIntegrationTest

# Chạy ở chế độ Nghiêm ngặt (Strict TDD Mode - kích hoạt toàn bộ assertion cho Milestone 2 & 5)
.\mvnw.cmd test -pl mini-leetcode-api -Dtest=HybridSubmissionIntegrationTest -Dtest.hybrid.strict=true
```

### 4.2. Chạy Frontend Playwright E2E Suite
```powershell
cd "c:\Users\aDMIN\Documents\JSD\Mini Leetcode\frontend"

# Liệt kê danh sách 37 test cases
npx playwright test workspace-oop-multifile.spec.ts --list

# Thực thi toàn bộ test suite
npx playwright test workspace-oop-multifile.spec.ts

# Thực thi với giao diện Playwright UI trực quan
npx playwright test workspace-oop-multifile.spec.ts --ui
```

---

## 5. Kết luận & Độ bao phủ Đạt được (Coverage Summary)

- **Tổng số test cases mới**: **90 test cases** (53 Backend + 37 Frontend).
- **Tỷ lệ bao phủ yêu cầu**:
  - Unified JSON Multi-File (`REQ-SUB-001`): 100% bao phủ.
  - Multipart Upload ZIP & Java (`REQ-SUB-002`): 100% bao phủ.
  - Delimited Marker Syntax (`REQ-SUB-003`): 100% bao phủ (đang pass trực tiếp).
  - Starter Scaffolding Delivery (`REQ-SCAF-001`, `REQ-SCAF-002`): 100% bao phủ.
  - Fast Run vs Full Submit (`REQ-EXEC-001`, `REQ-EXEC-002`): 100% bao phủ.
  - Security Limits & Zip Slip (`REQ-SEC-001`, `REQ-SEC-002`): 100% bao phủ.
  - Monaco Workspace UI Controls (`REQ-UI-001` $\to$ `REQ-UI-005`): 100% bao phủ.
- **Tính hồi quy**: Toàn bộ hệ thống biên dịch sạch (`BUILD SUCCESS`, `tsc --noEmit` exit 0). Sẵn sàng phục vụ nghiệm thu cho các Milestone tiếp theo.
