# Mini LeetCode Platform — Monorepo

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Multi--Module-blue.svg)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Next.js 14](https://img.shields.io/badge/Next.js-14.2-black.svg)](https://nextjs.org/)

> An online judge platform for algorithm practice and competitive programming, built around an isolated Docker sandbox, server-side exam proctoring, an asynchronous submission queue, and large-capacity testcase storage.

---

## 1. Team

Two engineers own the whole monorepo end to end.

| Member | Role | Ownership |
|---|---|---|
| **Trịnh Quốc Việt** (`supaFrik`) | **Backend** | Java modules under `backend/` — Spring Boot REST APIs, Spring Security JWT, MySQL + Flyway migrations, Redis Streams queue, the judge worker and the Docker sandbox runner. Also owns the repository's API contract and release tags. |
| **Đức Trí** (`TranDucTri`) | **Frontend** | The Next.js client under `frontend/` — App Router pages, the Monaco editor workspace, the "Ink & Mesh" design system, the exam/proctoring UI, and the Playwright e2e suite. |

**How the two halves meet.** The backend publishes a REST contract that the frontend consumes verbatim; there is no separate mock contract to keep in sync. `frontend/src/lib/services.ts` is the single client-side adapter — every response shape is normalised there before it reaches a component. The e2e suite in `frontend/e2e/fixtures/mock-api.ts` replays that same contract, so a change on either side fails CI on the other.

---

## 2. Repository layout

```
Mini Leetcode/
├── backend/                        # Java 17 LTS, Maven multi-module
│   ├── pom.xml                     # Parent POM — central version management
│   ├── mini-leetcode-common/       # Pure enums, base exceptions, API response DTOs
│   ├── mini-leetcode-core/         # JPA entities, repositories, domain services, Flyway, MinIO S3
│   ├── mini-leetcode-api/          # REST controllers, Spring Security JWT, OpenAPI 3.1 / Swagger
│   └── mini-leetcode-judge/        # Sandbox worker, Redis queue consumer, Docker runner
│   └── tools/                      # Seed scripts and operational SQL
├── frontend/                       # Next.js 14 (App Router) + React + Monaco Editor
│   ├── DESIGN.md                   # The "Ink & Mesh" design system
│   ├── e2e/                        # Playwright regression suite
│   └── .env.example                # Environment variables for API connectivity
├── docs/                           # Business & technical specification, ADRs
│   ├── TECHNICAL_AND_BUSINESS_DOCUMENTATION.md
│   ├── TECHNICAL_SPECIFICATION.md
│   ├── BUSINESS_REQUIREMENTS.md
│   ├── EXAM_ANTI_CHEAT_SPECIFICATION.md
│   ├── HYBRID_SUBMISSION_SPECIFICATION.md
│   ├── ADR-001_MODULAR_MONOLITH_ARCHITECTURE.md
│   ├── ADR-002_MAVEN_MULTI_MODULE_MONOLITH.md
│   ├── ADR-003_PACKAGE_BY_FEATURE_STRUCTURE.md
│   ├── ADR-004_PRAGMATIC_SANDBOX_DESIGN.md
│   └── team-pipeline/              # Agent team pipeline notes and onboarding
├── docker-compose.yml              # MySQL 8.0, Redis 7, MinIO + bucket provisioner
└── .gitignore
```

---

## 3. Tech stack

- **Backend:** Java 17 LTS, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Hibernate, Flyway.
- **Database:** MySQL 8.0 (InnoDB, `utf8mb4_unicode_ci`).
- **Cache & queue:** Redis 7 — asynchronous submission queue, verdict cache, consumer groups with zombie-job reclaim.
- **Object storage:** MinIO (S3-compatible) — testcase input/output files and submission artefacts.
- **Sandbox execution:** ephemeral Docker containers (`--network none`, `--memory 256m`, `--pids-limit 64`, read-only rootfs). Supports Java 17 LTS and Python 3.
- **Frontend:** Next.js 14 App Router, React 18, TypeScript, Tailwind CSS, Monaco Editor, Base UI primitives, next-themes (light-only).
- **Testing:** JUnit 5, Mockito, Testcontainers, H2 in-memory, Playwright (e2e).

---

## 4. Sandbox architecture — three roles

The security boundary is split across three responsibilities (see [ADR-004](docs/ADR-004_PRAGMATIC_SANDBOX_DESIGN.md)):

1. **Role 1 — Web API Gateway (`mini-leetcode-api`).** Accepts submissions, validates the payload, persists a `PENDING` record and pushes a message onto the Redis queue (`XADD queue.submissions`). It **never** mounts `/var/run/docker.sock`.
2. **Role 2 — Judge Worker (`mini-leetcode-judge`).** Consumes the queue (`XREADGROUP`), downloads testcases into `temp-submissions/{id}/`, drives the sandbox runner lifecycle through the Docker CLI, measures metrics and computes the verdict. A `ZombieJobReclaimer` (`XCLAIM`) recovers jobs orphaned by a crashed worker, and terminal-state checks make re-judging idempotent.
3. **Role 3 — Ephemeral Sandbox Runner.** A single-use container with no network access, a 256 MB memory cap, one CPU core, a 64-process limit against fork bombs, and testcase directories mounted read-only. It applies a **fail-fast** policy — the first failing testcase stops the run so resources are not wasted.

Student code is untrusted. The Docker container is the only security boundary that is enforced.

---

## 5. Feature surface

| Area | What it does |
|---|---|
| **Problems** | Catalogue with difficulty, topic tags and two tracks — `ALGORITHMIC` (DSA) and `OOP_GRINDING` (OOP + socket). Full lifecycle: `DRAFT → REVIEWING → PUBLISHED → ARCHIVED`, with staff-only management screens. |
| **Submissions** | Asynchronous judging through the Redis queue, with per-phase verdict, runtime and memory metrics, and a `score_breakdown` JSON document. |
| **OOP grinding** | A four-phase pipeline inside one container: multi-file compilation (fail-fast), OOP structure audit via bytecode reflection (30%), JUnit 5 unit tests (40%), and TCP/UDP socket integration tests on an ephemeral loopback port (30%). Score = `0.3·OOP + 0.4·Unit + 0.3·Socket`, with the weights renormalised to `40/60` for problems without a socket phase. |
| **Contests & exams** | Contest registration, an ACM/ICPC leaderboard, a server-side proctoring evaluator that consumes focus-loss events, and plagiarism reporting. |
| **Hybrid submission intake** | Multi-tab Monaco editing, `.zip` drag-and-drop, multi-file upload, a `// === FILE: Name.java ===` delimited format, and a single-file fallback — plus a downloadable starter project for offline work. |

---

## 6. Quick start

### Step 1 — Start the infrastructure (MySQL, Redis, MinIO)

From the repository root:

```bash
docker compose up -d
```

The `minio_provisioner` container pre-creates the `testcases` and `submissions` buckets.

### Step 2 — Build and test the backend

```bash
cd backend

# Run the full unit + integration suite
./mvnw clean test

# Package the executable Web API jar
./mvnw clean package -DskipTests
```

### Step 3 — Run the Web API

```bash
java -jar mini-leetcode-api/target/mini-leetcode-api-0.0.1-SNAPSHOT.jar
```

### Step 4 — Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The client is served on [http://localhost:3000](http://localhost:3000). Copy `frontend/.env.example` to `frontend/.env.local` and point it at the API before logging in.

### Step 5 — Run the e2e suite

```bash
cd frontend
npm run test:e2e
```

---

## 7. API documentation & observability

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.1 JSON spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health check probe:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **MinIO console:** [http://localhost:9001](http://localhost:9001) (`minioadmin` / `minioadminpassword`)

---

## 8. Further documentation

- [Full technical & business documentation](docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md)
- [Technical specification](docs/TECHNICAL_SPECIFICATION.md)
- [Business requirements](docs/BUSINESS_REQUIREMENTS.md)
- [Exam anti-cheat specification](docs/EXAM_ANTI_CHEAT_SPECIFICATION.md)
- [Hybrid submission specification](docs/HYBRID_SUBMISSION_SPECIFICATION.md)
- [ADR-001: Modular monolith architecture](docs/ADR-001_MODULAR_MONOLITH_ARCHITECTURE.md)
- [ADR-002: Maven multi-module monolith](docs/ADR-002_MAVEN_MULTI_MODULE_MONOLITH.md)
- [ADR-003: Package-by-feature structure](docs/ADR-003_PACKAGE_BY_FEATURE_STRUCTURE.md)
- [ADR-004: Pragmatic sandbox design](docs/ADR-004_PRAGMATIC_SANDBOX_DESIGN.md)
- [Frontend design system](frontend/DESIGN.md)
