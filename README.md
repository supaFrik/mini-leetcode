# Mini LeetCode Platform — Monorepo

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Multi--Module-blue.svg)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Next.js 14](https://img.shields.io/badge/Next.js-14.2-black.svg)](https://nextjs.org/)

> An online judge platform for algorithm practice, OOP projects and proctored exams, built around an isolated Docker sandbox, server-side exam proctoring, an asynchronous submission queue, project-based study plans and an IntelliJ IDEA plugin for coding and checking stages in the IDE.

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
│   ├── mini-leetcode-judge/        # Sandbox worker, Redis queue consumer, Docker runner
│   └── tools/                      # Sample problems, testcase generators, study plan seeds (dev only)
├── frontend/                       # Next.js 14 (App Router) + React + Monaco Editor
│   ├── DESIGN.md                   # The "Ink & Mesh" design system
│   ├── e2e/                        # Playwright regression suite
│   └── .env.example                # Environment variables for API connectivity
├── ide-plugin/                     # IntelliJ IDEA plugin (Gradle, JDK 21) for study plan projects
├── docs/                           # Business requirements and feature specifications
├── specs/                          # Feature specs (study plan, live console, OOP metrics)
├── docker-compose.yml              # MySQL 8.0, Redis 7, MinIO, API, judge, frontend
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
- **IDE plugin:** IntelliJ Platform Gradle Plugin 2.x, IntelliJ IDEA 2025.3+, JDK 21.
- **Testing:** JUnit 5, Mockito, Testcontainers, H2 in-memory, Playwright (e2e).

---

## 4. Sandbox architecture — three roles

The security boundary is split across three responsibilities:

1. **Role 1 — Web API Gateway (`mini-leetcode-api`).** Accepts submissions, validates the payload, persists a `PENDING` record and pushes a message onto the Redis queue (`XADD queue.submissions`). It **never** mounts `/var/run/docker.sock`.
2. **Role 2 — Judge Worker (`mini-leetcode-judge`).** Consumes the queue (`XREADGROUP`), downloads testcases into `temp-submissions/{id}/`, drives the sandbox runner lifecycle through the Docker CLI, measures metrics and computes the verdict. A `ZombieJobReclaimer` (`XCLAIM`) recovers jobs orphaned by a crashed worker, and terminal-state checks make re-judging idempotent.
3. **Role 3 — Ephemeral Sandbox Runner.** A single-use container with no network access, a 256 MB memory cap, one CPU core, a 64-process limit against fork bombs, and testcase directories mounted read-only. It applies a **fail-fast** policy — the first failing testcase stops the run so resources are not wasted.

Student code is untrusted. The Docker container is the only security boundary that is enforced.

---

## 5. Feature surface

| Area | What it does |
|---|---|
| **Problems** | Catalogue with difficulty, topic tags and two tracks — `ALGORITHMIC` (DSA) and `OOP_GRINDING` (OOP + socket). Lifecycle `DRAFT ⇄ REVIEWING → PUBLISHED → ARCHIVED → DRAFT`; publishing needs at least one public testcase and freezes the testcase set. Instructors and admins can create, edit and publish; only admins delete. |
| **Submissions** | Asynchronous judging through the Redis queue, with per-phase verdict, runtime and memory metrics, and a `score_breakdown` JSON document. `Run` (quick run) never counts towards progress. |
| **OOP grinding** | Multi-file compilation (fail-fast), then up to three server-side graded parts: an OOP structure audit via bytecode reflection, stdin/stdout testcases run in the sandbox, and TCP socket scenarios on a loopback port. Default weights are `30 / 40 / 30`, or `40 / 60` without sockets; a problem missing a part has its weights redistributed over the parts it has. Grading configuration lives on the server only. |
| **Study plans** | Instructor-authored learning paths made of multi-stage **projects** and standalone practice problems. Each stage is a regular problem; stage N unlocks once stage N-1 is accepted, and progress is derived from submissions. Sample plan: *OOP với Java* with the 5-stage **Mini Bank Console** project. |
| **IntelliJ plugin** | Browse study plans, open a stage as a Maven project with its statement, and check it against the server, authenticated with a personal access token (revocable, stored in the IDE's Password Safe, rejected for exam submissions). |
| **Contests & exams** | Contest registration, an ACM/ICPC leaderboard, a server-side proctoring evaluator that consumes focus-loss events (1.5 s grace period, lock after the configured number of violations), and plagiarism reporting. |
| **Hybrid submission intake** | Multi-tab Monaco editing, `.zip` drag-and-drop, multi-file upload, a `// === FILE: Name.java ===` delimited format, and a single-file fallback — plus a downloadable starter project for offline work. |

Not implemented yet: IOI-style partial-score contests (defined in the business rules, the leaderboard is ACM only), account / role management APIs (promote an admin with `backend/tools/promote-admin.ps1`), and a contest creation API (contests are seeded with `backend/tools/seed-contests.sql`).

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

### Step 6 — Sample content and the IntelliJ plugin (optional)

- Sample problems, testcases and the *OOP với Java* study plan: see [backend/tools/README.md](backend/tools/README.md).
- Build the plugin with `cd ide-plugin && ./gradlew buildPlugin`, then `cd frontend && npm run plugin:copy` so the **IntelliJ plugin** page (`/ide`) serves the ZIP. That page walks students through installing it (*Settings › Plugins › ⚙ › Install Plugin from Disk*, IntelliJ IDEA 2025.3 or newer) and creating a personal access token.

---

## 7. API documentation & observability

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.1 JSON spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health check probe:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **MinIO console:** [http://localhost:9001](http://localhost:9001) (`minioadmin` / `minioadminpassword`)

---

## 8. Further documentation

- [Business requirements](docs/BUSINESS_REQUIREMENTS.md)
- [Full technical & business documentation](docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md)
- [Exam anti-cheat specification](docs/EXAM_ANTI_CHEAT_SPECIFICATION.md)
- [Hybrid submission specification](docs/HYBRID_SUBMISSION_SPECIFICATION.md)
- [Study plan & IntelliJ plugin specification](specs/study-plan.spec.md)
- [Live terminal console](specs/live-terminal-console.spec.md)
- [OOP runtime metrics](specs/oop-console-runtime-metrics.spec.md)
- [Authoring problems, testcases and OOP grading](backend/tools/README.md)
- [Frontend design system](frontend/DESIGN.md)
