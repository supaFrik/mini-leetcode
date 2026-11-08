# Mini LeetCode Platform

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js 14](https://img.shields.io/badge/Next.js-14-black.svg)](https://nextjs.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)

> Nền tảng luyện tập và thi lập trình trực tuyến (Online Judge): học viên giải bài thuật toán và bài OOP/Socket Java, được chấm tự động trong sandbox Docker cô lập; giảng viên ra đề, tổ chức kỳ thi có giám sát liêm chính và bảng xếp hạng.

---

## 1. Bài toán nghiệp vụ

Hệ thống phục vụ hai chế độ song song:

| Chế độ | Mục tiêu | Đặc điểm |
|---|---|---|
| **Luyện tập (Practice)** | Rèn tư duy thuật toán & OOP, phản hồi nhanh | Không giới hạn số lần nộp, không phạt hành vi, theo dõi tiến độ từng bài |
| **Thi / Kiểm tra (Exam / Contest)** | Đánh giá năng lực có kiểm soát | Tính giờ theo server, giám sát rời tab, khoá bài khi vi phạm quá ngưỡng, bảng xếp hạng |

### Vai trò người dùng

| Vai trò | Quyền chính |
|---|---|
| **USER** (Học viên / Thí sinh) | Xem bài đã phát hành, chạy thử (Run) với testcase công khai, nộp bài (Submit), xem lịch sử bài nộp, đăng ký & tham gia kỳ thi |
| **INSTRUCTOR** (Giảng viên / Người ra đề) | Tạo & sửa đề, tải testcase (công khai / ẩn), cấu hình chấm OOP, chuyển trạng thái đề (kể cả phát hành), xem báo cáo giám sát & đạo văn của kỳ thi |
| **ADMIN** | Mọi quyền của Instructor, thêm: xoá đề, mở khoá thí sinh bị khoá trong kỳ thi |

> Chưa có API quản lý tài khoản / phân quyền: nâng một tài khoản lên ADMIN bằng `backend/tools/promote-admin.ps1`; kỳ thi hiện được tạo bằng dữ liệu seed (`backend/tools/seed-contests.sql`).

---

## 2. Chức năng chính

### 2.1 Ngân hàng đề bài
- Vòng đời đề: `DRAFT` ⇄ `REVIEWING` → `PUBLISHED` → `ARCHIVED` → `DRAFT`. Chỉ đề `PUBLISHED` xuất hiện với học viên; muốn phát hành phải có ít nhất một testcase công khai; testcase của đề `PUBLISHED` bị khoá (muốn sửa phải đưa đề về `DRAFT`).
- Thuộc tính: tiêu đề, slug, đề Markdown, độ khó (`EASY` / `MEDIUM` / `HARD`), chủ đề (tags), giới hạn thời gian & bộ nhớ.
- Hai loại đề (`ProblemType`):
  - **`ALGORITHMIC`** — chấm theo testcase stdin/stdout.
  - **`OOP_GRINDING`** — bài OOP / Socket Java nhiều file, có starter template tải về dạng ZIP.
- Danh mục bài lọc được theo độ khó, chủ đề, từ khoá, loại đề và **tiến độ cá nhân** (`DONE` / `IN_PROGRESS` / `NEVER`).

### 2.2 Làm bài & nộp bài
- Workspace chia đôi: đề bài bên trái, Monaco Editor bên phải; ngôn ngữ Java 17 và Python 3.
- **Run** (chạy thử, không tính là bài nộp) và **Submit** (nộp chính thức).
- Bài OOP nộp được qua editor nhiều file, file ZIP hoặc nhiều file `.java`.
- **Console trực tiếp**: chạy chương trình trong sandbox, xem output theo thời gian thực và nhập stdin như terminal của IDE.
- Kết quả (verdict): `AC`, `WA`, `TLE`, `MLE`, `CE`, `RE`, `SV` (vi phạm sandbox), `FLAGGED_CHEATING`; kèm runtime, bộ nhớ, số testcase đạt.

### 2.3 Quy chế chấm điểm
- **Bài thuật toán:** đúng 100% testcase ẩn ⇒ `AC`; dừng ở testcase lỗi đầu tiên (fail-fast).
- **Bài OOP (điểm thành phần):**

  | Thành phần | Cách chấm | Trọng số (có socket) | Trọng số (không socket) |
  |---|---|:---:|:---:|
  | Cấu trúc OOP | Kiểm tra lớp/interface/kế thừa/đóng gói/chữ ký hàm theo rule giảng viên định nghĩa | 30% | 40% |
  | Testcase stdin/stdout | Chạy `main` trong sandbox, so output | 40% | 60% |
  | Kịch bản Socket | Kiểm thử client/server TCP trên loopback | 30% | — |

  Đây là trọng số mặc định khi đề có đủ thành phần; đề thiếu thành phần nào thì trọng số tự chia lại cho các thành phần còn lại (vd. chỉ có rule + socket ⇒ 50/50). Cấu hình chấm chỉ nằm phía server; mọi file cấu hình/test do học viên nộp kèm đều bị bỏ qua.
- **Kỳ thi:** xếp hạng theo **ACM/ICPC** — số bài AC, rồi tổng thời gian phạt `Σ (T_AC + 20 × số lần sai trước AC)`. Quy chế IOI (điểm theo nhóm testcase) có trong [tài liệu nghiệp vụ](docs/BUSINESS_REQUIREMENTS.md) nhưng chưa được triển khai.

### 2.4 Kỳ thi & giám sát liêm chính
- Kỳ thi có giờ bắt đầu/kết thúc theo server, danh sách đề gán nhãn A, B, C…, thí sinh đăng ký trước.
- Theo dõi rời tab / mất focus / paste; **server là nơi quyết định** số lần vi phạm, có thời gian ân hạn 1.5 giây.
- Vi phạm lần 1–2: cảnh báo toàn màn hình; đạt ngưỡng (mặc định 3): khoá editor, tự thu bài, gắn cờ.
- Tự lưu bản nháp & cảnh báo khi mất mạng để không mất bài.
- Thí sinh chốt bài (hoặc được tự chốt khi hết giờ); Giảng viên xem báo cáo giám sát và danh sách nghi đạo văn; Admin mở khoá thí sinh bị khoá.

### 2.5 Theo dõi tiến độ cá nhân
- Lịch sử bài nộp, xem lại code đã nộp, thống kê số bài nộp theo từng verdict.

---

## 3. Công nghệ

- **Backend:** Java 17, Spring Boot 3.3.4, Spring Security (JWT), Spring Data JPA, Flyway, OpenAPI/Swagger.
- **Frontend:** Next.js 14 (App Router), TypeScript, Tailwind CSS, Monaco Editor.
- **Hạ tầng:** MySQL 8.0, Redis 7 (hàng đợi bài nộp), MinIO (lưu testcase & mã nguồn nộp).
- **Chấm bài:** Docker container dùng một lần, không mạng, giới hạn RAM/CPU/số tiến trình.

### Cấu trúc thư mục

```
Mini Leetcode/
├── backend/
│   ├── mini-leetcode-common/   # Enum, exception, DTO dùng chung
│   ├── mini-leetcode-core/     # Entity, repository, service nghiệp vụ, Flyway migration
│   ├── mini-leetcode-api/      # REST API, bảo mật JWT, Swagger
│   ├── mini-leetcode-judge/    # Worker chấm bài (thuật toán & OOP) chạy sandbox Docker
│   └── tools/                  # Đề bài mẫu, sinh testcase, cấu hình chấm OOP (dev)
├── frontend/                   # Ứng dụng web Next.js
├── docs/                       # Tài liệu nghiệp vụ & đặc tả
├── specs/                      # Đặc tả tính năng
└── docker-compose.yml          # MySQL, Redis, MinIO, API, Judge, Frontend
```

---

## 4. Chạy cục bộ

### Cách 1 — Toàn bộ bằng Docker Compose
```bash
docker compose up -d --build
```
| Dịch vụ | Địa chỉ |
|---|---|
| Frontend | http://localhost:3100 (đổi bằng `FRONTEND_PORT`) |
| API / Swagger UI | http://localhost:8080/swagger-ui.html |
| MinIO Console | http://localhost:9001 (`minioadmin` / `minioadminpassword`) |

MySQL map ra cổng `3306` (đổi bằng `MYSQL_HOST_PORT`). Bucket `testcases` và `submissions` được tạo tự động.

### Cách 2 — Chạy từng phần khi phát triển
```powershell
# Hạ tầng
docker compose up -d mysql redis minio minio_provisioner

# Backend
cd backend
.\mvnw clean test                      # chạy test
.\mvnw clean package -DskipTests
java -jar mini-leetcode-api/target/mini-leetcode-api-0.0.1-SNAPSHOT.jar

# Frontend
cd frontend
copy .env.example .env.local           # NEXT_PUBLIC_USE_MOCK=false để gọi backend thật
npm install
npm run dev
```

### Nạp đề bài mẫu
Xem [backend/tools/README.md](backend/tools/README.md): sinh seed SQL cho 20 đề mẫu (thuật toán, OOP, Socket), sinh testcase và nạp qua API.

---

## 5. Tài liệu nghiệp vụ

| Tài liệu | Nội dung |
|---|---|
| [docs/BUSINESS_REQUIREMENTS.md](docs/BUSINESS_REQUIREMENTS.md) | Vai trò, vòng đời đề, quy trình kỳ thi, quy chế chấm điểm & xếp hạng, quy chế giám sát, tiêu chí nghiệm thu |
| [docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md](docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md) | Đặc tả tổng hợp nghiệp vụ & hệ thống |
| [docs/EXAM_ANTI_CHEAT_SPECIFICATION.md](docs/EXAM_ANTI_CHEAT_SPECIFICATION.md) | Phân định chế độ Thi / Luyện tập và quy tắc chống gian lận |
| [docs/HYBRID_SUBMISSION_SPECIFICATION.md](docs/HYBRID_SUBMISSION_SPECIFICATION.md) | Nộp bài OOP đa hình thức (editor, ZIP, nhiều file) & starter template |
| [specs/live-terminal-console.spec.md](specs/live-terminal-console.spec.md) | Console tương tác stdin/stdout khi chạy thử |
| [specs/oop-console-runtime-metrics.spec.md](specs/oop-console-runtime-metrics.spec.md) | Runtime & bộ nhớ thực cho bài OOP |
| [backend/tools/README.md](backend/tools/README.md) | Cách ra đề, nạp testcase và cấu hình chấm bài OOP |
