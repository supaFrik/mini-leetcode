# Mini LeetCode Platform — Monorepo

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Multi--Module-blue.svg)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Tests Passing](https://img.shields.io/badge/Tests-79%2F79%20Passed-success.svg)]()

> Nền tảng thi đấu và rèn luyện thuật toán trực tuyến (Online Judge) với cơ chế Sandbox cô lập an toàn, giám sát thi cử liêm chính (Server-Side Proctoring), hàng đợi bài nộp bất đồng bộ và bộ lưu trữ testcase dung lượng lớn.

---

## 1. Cấu trúc Dự án (Monorepo Directory Layout)

```
Mini Leetcode/
├── backend/                        # Java 17 LTS Maven Multi-Module
│   ├── pom.xml                     # Parent POM quản lý phiên bản tập trung
│   ├── mini-leetcode-common/       # Java Enums thuần, Base Exceptions, API Response DTOs
│   ├── mini-leetcode-core/         # JPA Entities, Repositories, Domain Services, Flyway, MinIO S3
│   ├── mini-leetcode-api/          # REST Controllers, Spring Security JWT, OpenAPI 3.1 Swagger
│   └── mini-leetcode-judge/        # Sandbox Worker, Redis Queue Consumer, Docker Runner
├── frontend/                       # Client Tier (Next.js 14 / React + Monaco Editor)
│   ├── README.md                   # Hướng dẫn tích hợp & Contract API cho Frontend Engineer
│   └── .env.example                # Cấu hình biến môi trường kết nối API
├── docs/                           # Hồ sơ Tài liệu Nghiệp vụ & Kiến trúc Kỹ thuật
│   ├── Mini_LeetCode_Technical_and_Business_Specs.docx  # Tài liệu chuẩn dạng Word (.docx)
│   ├── TECHNICAL_AND_BUSINESS_DOCUMENTATION.md          # Đặc tả chi tiết toàn diện (.md)
│   ├── TECHNICAL_SPECIFICATION.md                       # Đặc tả kiến trúc hệ thống
│   ├── BUSINESS_REQUIREMENTS.md                         # Nghiệp vụ bài toán & Rule thi cử
│   ├── ADR-001_MODULAR_MONOLITH_ARCHITECTURE.md
│   ├── ADR-002_MAVEN_MULTI_MODULE_MONOLITH.md
│   ├── ADR-003_PACKAGE_BY_FEATURE_STRUCTURE.md
│   ├── ADR-004_PRAGMATIC_SANDBOX_DESIGN.md
│   └── team-pipeline/              # Đội ngũ Team Agent Pipeline
├── docker-compose.yml              # MySQL 8.0, Redis 7, MinIO (elestio/minio) + Bucket Provisioner
└── .gitignore                      # Cấu hình bỏ qua tệp tin toàn diện Monorepo
```

---

## 2. Công nghệ Cốt lõi (Tech Stack)

- **Backend:** Java 17 LTS, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Hibernate, Flyway.
- **Database:** MySQL 8.0 (InnoDB, `utf8mb4_unicode_ci`).
- **Cache & Queue:** Redis 7 (Alpine) — Hàng đợi bài nộp bất đồng bộ & bộ đệm kết quả.
- **Object Storage:** MinIO S3-compatible Object Storage — Lưu trữ file input/output testcase lớn.
- **Sandbox Execution:** Docker CLI Ephemeral Containers (`--network none`, `--memory 256m`, `--pids-limit 64`, Read-Only mounts). Hỗ trợ nộp bài bằng **Java 17 LTS** và **Python 3**.
- **Documentation & Testing:** OpenAPI 3.1 / Swagger UI, JUnit 5, Mockito, Testcontainers, H2 in-memory.

---

## 3. Kiến trúc Sandbox 3 Roles (Pragmatic Online Judge)

Hệ thống phân định ranh giới bảo mật tuyệt đối thành 3 Role chuyên trách (xem chi tiết tại [ADR-004](file:///docs/ADR-004_PRAGMATIC_SANDBOX_DESIGN.md)):
1. **Role 1 - Web API Gateway (`mini-leetcode-api`):** Nhận code nộp, kiểm tra payload, lưu bản ghi `PENDING`, đẩy message vào Redis Queue (`RPUSH queue.submissions`). **Tuyệt đối không mount `/var/run/docker.sock`**.
2. **Role 2 - Judge Worker (`mini-leetcode-judge`):** Lắng nghe queue (`BLPOP`), tải file testcase về thư mục tạm `temp-submissions/{id}/`, điều phối vòng đời của Sandbox Runner qua Docker CLI, đo lường metrics và tính toán Verdict.
3. **Role 3 - Ephemeral Sandbox Runner:** Container dùng 1 lần, cách ly mạng 100% (`--network none`), giới hạn RAM 256MB, CPU 1 core, chống Fork Bomb (`--pids-limit 64`), mount thư mục testcase Read-Only. Áp dụng chính sách **Fail-Fast** (dừng ngay ở testcase lỗi đầu tiên để tiết kiệm tài nguyên).

---

## 4. Hướng dẫn Khởi chạy Cục bộ (Quick Start)

### Bước 1: Khởi động Hạ tầng Docker (MySQL, Redis, MinIO)
Từ thư mục gốc dự án:
```bash
docker compose up -d
```
Container `minio_provisioner` sẽ tự động tạo sẵn 2 bucket: `testcases` và `submissions`.

### Bước 2: Biên dịch & Kiểm thử Backend
```bash
cd backend

# Chạy toàn bộ 79 bài kiểm thử tích hợp & đơn vị
.\mvnw clean test

# Đóng gói file JAR thực thi cho Web API
.\mvnw clean package -DskipTests
```

### Bước 3: Khởi chạy Ứng dụng Web API
```bash
java -jar mini-leetcode-api/target/mini-leetcode-api-0.0.1-SNAPSHOT.jar
```

---

## 5. Tài liệu API & Giám sát

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.1 JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check Probe:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **MinIO Console:** [http://localhost:9001](http://localhost:9001) (`minioadmin` / `minioadminpassword`)

---

## 6. Hồ sơ Tài liệu Kỹ thuật Chi tiết
- [Tài liệu Nghiệp vụ & Kiến trúc Toàn diện (Markdown)](file:///docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md)
- [Tài liệu Word hoàn chỉnh (.docx)](file:///docs/Mini_LeetCode_Technical_and_Business_Specs.docx)
- [ADR-001: Modular Monolith Architecture](file:///docs/ADR-001_MODULAR_MONOLITH_ARCHITECTURE.md)
- [ADR-002: Maven Multi-Module Monolith](file:///docs/ADR-002_MAVEN_MULTI_MODULE_MONOLITH.md)
- [ADR-003: Package-by-Feature Structure](file:///docs/ADR-003_PACKAGE_BY_FEATURE_STRUCTURE.md)
- [ADR-004: Pragmatic Sandbox Design](file:///docs/ADR-004_PRAGMATIC_SANDBOX_DESIGN.md)
