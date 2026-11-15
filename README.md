# Mini LeetCode Platform — Monorepo

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Multi--Module-blue.svg)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Next.js 14](https://img.shields.io/badge/Next.js-14.2-black.svg)](https://nextjs.org/)

> An online judge platform for algorithm practice, OOP projects and proctored exams, built around an isolated Docker sandbox, server-side exam proctoring, an asynchronous submission queue, project-based study plans with lessons, and an IntelliJ IDEA plugin for coding and checking stages in the IDE.

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
│   ├── mini-leetcode-core/         # JPA entities, repositories, domain services, Flyway (V1–V13), MinIO S3
│   ├── mini-leetcode-api/          # REST controllers, Spring Security JWT + personal access tokens, OpenAPI 3.1
│   ├── mini-leetcode-judge/        # Sandbox worker, Redis queue consumer, Docker runner, reference-run service
│   └── tools/                      # Seeds, testcase generators and the problem content pipeline (dev only)
├── frontend/                       # Next.js 14 (App Router) + React + Monaco Editor
│   ├── DESIGN.md                   # The "Ink & Mesh" design system
│   ├── e2e/                        # Playwright regression suite
│   └── .env.example                # Environment variables for API connectivity
├── ide-plugin/                     # IntelliJ IDEA plugin (Gradle, JDK 21) for study plan projects
├── docs/                           # Business requirements and technical documentation
├── specs/                          # Feature specs (study plans, topics, course builder, admin, plugin…)
├── docker-compose.yml              # MySQL 8.0, Redis 7, MinIO, API, judge, frontend
└── .gitignore
```

---

## 3. Tech stack

- **Backend:** Java 17 LTS, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Hibernate, Flyway.
- **Database:** MySQL 8.0 (InnoDB, `utf8mb4_unicode_ci`).
- **Cache & queue:** Redis 7 — asynchronous submission queue (Streams + consumer groups) with zombie-job reclaim.
- **Object storage:** MinIO (S3-compatible) — testcase input/output files and submission artefacts.
- **Sandbox execution:** ephemeral Docker containers (`--network none`, memory/CPU/pids limits, read-only mounts) on `eclipse-temurin:17-jdk` (Java) and `python:3.12-slim` (Python).
- **Frontend:** Next.js 14 App Router, React 18, TypeScript, Tailwind CSS, Monaco Editor, Tiptap (visual Markdown editor), Base UI primitives.
- **IDE plugin:** IntelliJ Platform Gradle Plugin 2.x, IntelliJ IDEA 2025.3+, JDK 21.
- **Testing:** JUnit 5, Mockito, Testcontainers, H2 in-memory, Playwright (e2e).

---

## 4. Sandbox architecture — three roles

The security boundary is split across three responsibilities:

1. **Role 1 — Web API Gateway (`mini-leetcode-api`).** Accepts submissions, validates the payload, persists a `PENDING` record and pushes a message onto the Redis stream. It **never** mounts `/var/run/docker.sock`.
2. **Role 2 — Judge Worker (`mini-leetcode-judge`).** Consumes the stream (`XREADGROUP`), downloads testcases into `temp-submissions/{id}/`, drives the sandbox lifecycle through the Docker CLI, measures metrics and computes the verdict. A `ZombieJobReclaimer` (`XCLAIM`) recovers jobs orphaned by a crashed worker, and terminal-state checks make re-judging idempotent. It also runs instructors' reference solutions (`/admin/problems/{id}/reference-run`).
3. **Role 3 — Ephemeral Sandbox Runner.** A single-use container with no network access, a memory cap, one CPU core, a process limit against fork bombs, and testcase directories mounted read-only. It applies a **fail-fast** policy — the first failing testcase stops the run.

Student code is untrusted. The Docker container is the only security boundary that is enforced.

---

## 5. Feature surface

| Area | What it does |
|---|---|
| **Problems** | Catalogue with difficulty, topic tags and two tracks — `ALGORITHMIC` (DSA) and `OOP_GRINDING` (multi-file Java). Lifecycle `DRAFT ⇄ REVIEWING → PUBLISHED → ARCHIVED → DRAFT`; publishing needs a public testcase and freezes testcases/grading (instructors **Unlock** to edit). A problem's slug is fixed at creation, so renaming never breaks links. Guests can read problems; submitting asks them to log in. |
| **Authoring (admin)** | Full-page problem editor: visual (Notion-style) statement editor that stores Markdown, per-language starter code (DSA: `Solution.java` / `solution.py`; OOP: multiple files), testcases with labels, grading config, reference solution with "check against reference". Statements can also be edited visually from the problem page itself. |
| **Submissions** | Asynchronous judging with verdict, runtime and memory metrics. `Run` uses public testcases only and never counts towards progress; `Submit` uses all of them. |
| **OOP grading** | Multi-file compilation, then **stdin → stdout testcases** run in the sandbox (Accepted = every case matches, compared per `grading_config.output`, e.g. `normalized`). Optional extras: structural rules checked by reflection and TCP socket scenarios; when present they are weighted with the output part. Grading configuration lives on the server only. |
| **Study plans & lessons** | Instructor-authored learning paths (Course Builder) made of multi-stage **projects** and practice problems, with lesson **topics** (theory + exercises, 3-strike practice lockout) attached to stages. Stage N can be neither opened nor submitted until stage N-1 is accepted. Sample plan: *OOP in Java: Mini Bank* (5-stage **Mini Bank Console**). |
| **IntelliJ plugin** | Browse study plans, open a stage as a Maven project, check it against the server; authenticated with a revocable personal access token (Password Safe; rejected for exam submissions). Next version (in-IDE statement + Run/Check panel): [`specs/plugin-in-ide-solving.spec.md`](specs/plugin-in-ide-solving.spec.md). |
| **Contests & exams** | Admin contest CRUD and participant management, ACM/ICPC leaderboard, server-side proctoring (focus-loss events, lock after N violations) and plagiarism reporting. |
| **Hybrid submission intake** | Multi-tab Monaco editing, `.zip` drag-and-drop, multi-file upload, a `// === FILE: Name.java ===` delimited format, and a downloadable starter project. |

**Not implemented yet:** IOI-style partial-score contests (leaderboard is ACM only), user / role management in the UI (promote an account with `backend/tools/promote-admin.ps1` or the `BOOTSTRAP_ADMIN_*` variables), bulk testcase import.

---

## 6. Quick start (Docker — recommended)

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| Docker Desktop | recent; give it **≥ 6 GB RAM** (Settings › Resources) | the whole stack + sandbox containers |
| JDK | 17 | building the backend JARs |
| Node.js | 20+ | frontend dev mode and e2e tests (optional for Docker-only use) |
| JDK | 21 | the IntelliJ plugin (optional) |
| Python | 3.10+ | problem content tools (optional) |

> With the default 2 GB Docker Desktop memory the engine can be killed while the stack and sandboxes run (seen on Windows); raise it before starting.

### Step 1 — Build the backend JARs

The API and judge images copy prebuilt JARs, so build them first (from the repository root):

```bash
cd backend
./mvnw clean package          # full test suite (~8 min); add -DskipTests for a quick build
cd ..
```

### Step 2 — Pull the sandbox images

The judge starts student code in these images; pulling them up front avoids a slow (or timed-out) first submission:

```bash
docker pull eclipse-temurin:17-jdk
docker pull python:3.12-slim
docker tag eclipse-temurin:17-jdk mini-leetcode-judge-runner:java17   # OOP runner image name
```

### Step 3 — Start the stack

Optionally create a first admin account by putting these in a `.env` file next to `docker-compose.yml`:

```dotenv
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=change-me-please
```

Then:

```bash
docker compose up -d --build
```

| Service | URL |
|---|---|
| Web app | <http://localhost:3100> |
| API / Swagger | <http://localhost:8080/swagger-ui.html> |
| MinIO console | <http://localhost:9001> (`minioadmin` / `minioadminpassword`) |

**Port already in use?** Create `docker-compose.override.yml` (git-ignored) to remap host ports, e.g. when 3306 or 8080 is taken:

```yaml
services:
  mysql:
    ports: !override
      - "3307:3306"
  mini-leetcode-api:
    ports: !override
      - "8088:8080"
```

The web app reaches the API over the Docker network, so remapping the API host port does not affect it.

### Step 4 — Rebuild after changes

```bash
cd backend && ./mvnw package -DskipTests && cd ..
docker compose up -d --build             # or: docker compose up -d --build frontend
```

### Step 5 — Sample content

- Problems, testcases, contests and the *OOP in Java* plan: [backend/tools/README.md](backend/tools/README.md).
- Rewrite/refresh the 25 DSA + OOP problems (English statements, Java reference solutions, verified testcases) through the admin API — needs JDK 17 on `PATH`:

  ```bash
  python backend/tools/problems/content/rewrite_content.py --user admin --password '<password>'
  python backend/tools/problems/content/rewrite_content.py --only 1 101 --dry-run   # local check only
  ```

  The script runs each reference solution locally to produce expected outputs, cross-checks every case with the server's sandbox, then publishes. Its default `--base` is `http://localhost:3100/api/v1` (the web app proxies `/api`).

### Step 6 — The IntelliJ plugin (optional)

```bash
cd ide-plugin && ./gradlew buildPlugin     # needs JDK 21 (JAVA_HOME)
cd ../frontend && npm run plugin:copy      # serves the ZIP on the /ide page
```

The `/ide` page walks students through installing it (*Settings › Plugins › ⚙ › Install Plugin from Disk*, IntelliJ IDEA 2025.3+) and creating a personal access token. In *Settings › Tools › Mini LeetCode* set **Server URL to the web app address** (e.g. `http://localhost:3100`) — the plugin's built-in default is `http://localhost:8080`. `./gradlew runIde` starts a sandbox IDE with the plugin for testing.

---

## 7. Development mode (without the frontend container)

```bash
cd frontend
cp .env.example .env.local    # then set NEXT_PUBLIC_USE_MOCK=false, NEXT_PUBLIC_ENABLE_RUN=true,
                              # BACKEND_URL=<API address>, and PORT=3200 if 3100 is taken by Docker
npm install
npm run dev
```

`NEXT_PUBLIC_USE_MOCK=true` (the example default) runs the UI on in-browser fake data with no backend.

---

## 8. Tests

| Suite | Command | Result on 2026-11-15 |
|---|---|---|
| Backend (unit + integration) | `cd backend && ./mvnw test` | **955 passed**, 0 failed, 7 skipped (common 6 · core 234 · api 312 · judge 403) |
| Frontend type check / lint | `cd frontend && npx tsc --noEmit && npm run lint` | clean |
| Frontend e2e | `cd frontend && npm run test:e2e` (own dev server on 3005) or `BASE_URL=http://localhost:3100 npx playwright test` | **165 passed** (one timing test, `adversarial-exam` grace period, is occasionally flaky — passes on re-run) |
| IntelliJ plugin | `cd ide-plugin && ./gradlew test` | **387 passed** |

---

## 9. API documentation & observability

- **Swagger UI:** <http://localhost:8080/swagger-ui.html>
- **OpenAPI 3.1 JSON:** <http://localhost:8080/v3/api-docs>
- **Health probe:** <http://localhost:8080/actuator/health>

(Use your remapped host port if you changed it in `docker-compose.override.yml`.)

---

## 10. Further documentation

- [Business requirements](docs/BUSINESS_REQUIREMENTS.md) · [Technical & business documentation](docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md)
- [Exam anti-cheat specification](docs/EXAM_ANTI_CHEAT_SPECIFICATION.md) · [Hybrid submission specification](docs/HYBRID_SUBMISSION_SPECIFICATION.md)
- Study plans & plugin: [study-plan](specs/study-plan.spec.md), [topics](specs/topics.spec.md), [course builder](specs/course-builder.spec.md), [plugin in-IDE solving (draft)](specs/plugin-in-ide-solving.spec.md)
- Admin: [roadmap](specs/admin-roadmap.md), [contests](specs/admin-contest-management.spec.md), [user management](specs/admin-user-management.spec.md)
- Judge: [live terminal console](specs/live-terminal-console.spec.md), [OOP runtime metrics](specs/oop-console-runtime-metrics.spec.md)
- [Authoring problems, testcases and OOP grading](backend/tools/README.md) · [Frontend design system](frontend/DESIGN.md)
