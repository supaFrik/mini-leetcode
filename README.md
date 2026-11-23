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
| **OOP grading** | Multi-file compilation, then **stdin → stdout testcases** run in the sandbox (Accepted = every case matches, compared per `grading_config.output`, e.g. `normalized`). Structural reflection rules and TCP socket scenarios are evaluated and weighted automatically with testcase scores. Grading configuration lives on the server only. |
| **Study plans & lessons** | Instructor-authored learning paths (Course Builder) made of multi-stage **projects** and practice problems, with lesson **topics** attached to stages. Stage N can be neither opened nor submitted until stage N-1 is accepted. Sample plans: *OOP in Java: Mini Bank* (5-stage **Mini Bank Console**) and *Mastering Java OOP* (8 stages). |
| **Interactive exercises** | Rich micro-learning steps inside topics: **Single/Multiple Choice**, **Drag-and-Drop Matching**, **Sequence Ordering**, and **Fill-in-the-Blank code tokens**. Features progressive theory reading, hints, explanations, and an anti-brute-force **3-strike practice lockout** (5-minute cooldown + mandatory theory review). |
| **IntelliJ plugin** | Deep in-IDE problem solving & evaluation ([specs/plugin-in-ide-solving.spec.md](specs/plugin-in-ide-solving.spec.md)). Features dual-tab Tool Window: **Tab 1: Study Plans** (interactive tree syllabus) and **Tab 2: Problem View** (rich HTML statement, action bar with Run / Submit / Next Stage buttons, and collapsible Result Panel with testcase diagnostics and OOP inspection breakdown). Zero file clutter (`stages/*.md` eliminated), built-in Netty IDE bridge service (`http://localhost:63342`) for 1-click web connect, and secure token storage via IntelliJ Platform `PasswordSafe`. |
| **Contests & exams** | Admin contest CRUD and participant management, ACM/ICPC leaderboard, server-side proctoring (focus-loss events, lock after N violations) and plagiarism reporting. |
| **Hybrid submission intake** | Multi-tab Monaco editing, `.zip` drag-and-drop, multi-file upload, a `// === FILE: Name.java ===` delimited format, and a downloadable starter project. |

**Not implemented yet:** IOI-style partial-score contests (leaderboard is ACM only), user / role management in the UI (promote an account with `backend/tools/promote-admin.ps1` or the `BOOTSTRAP_ADMIN_*` variables), bulk testcase import.

### 5.1. Interactive Topic Exercises & Micro-Learning Engine

Attached to each project stage is an instructor-authored timeline of bite-sized lesson topics that ensure conceptual readiness before tackling complex implementation code:

| Exercise / Step Type | UI Interaction & Pedagogical Role | Feedback & Validation |
|---|---|---|
| **Single Choice (Radio)** | Selects exactly 1 option for concept checks or syntax identification. | Instant evaluation; provides *See hint* on wrong attempts and comprehensive *Explanation* on success. |
| **Multiple Choice (Checkbox)** | Selects all applicable options (multi-select) for complex architectural trade-offs. | Strict exact-match grading on the option set; prevents brute-force guessing. |
| **Matching (Drag-and-Drop)** | Pairs terms and definitions using mouse drag-and-drop or keyboard `↑` / `↓` buttons. | Server securely shuffles right-side items per step ID; returns progressive hints (e.g., `2/4 pairs correct`). |
| **Ordering (Sequence)** | Re-arranges execution statements or lifecycle stages into chronological order. | Normalized sequence comparison; stable seed shuffling on initial load. |
| **Fill-in-the-Blank** | Inlines input fields directly into syntax-highlighted code blocks at `[[id]]` placeholders. | Whitespace-normalized token matching with configurable case sensitivity (`caseSensitive`). |
| **Sectioned Theory** | Rich Markdown reader featuring dynamic TOC, section-by-section reading, and *Expand all*. | Tracks student reading progress; marks step complete upon finish. |
| **Code Challenge** | Links directly to a project or standalone problem. | Automatically marked `DONE` when the student achieves an `Accepted` sandbox verdict. |

#### Pedagogical Anti-Brute-Force Guardrail: 3-Strike Practice Lockout
To ensure students do not guess through interactive exercises:
- **3 consecutive wrong attempts** on any practice step trigger a **5-minute practice lockout** (`topic_locks` table).
- All theory steps in that topic are marked **unread**.
- **Cooldown & Unlock:** The student must wait out the 5-minute timer **and** reread all topic theory sections before practice unlocks. Submissions during lockout receive `409 Conflict (TOPIC_LOCKED)`.

*(Full user guide and authoring walkthrough: [docs/INTERACTIVE_TOPICS_AND_EXERCISES.md](docs/INTERACTIVE_TOPICS_AND_EXERCISES.md))*

---

## Run locally (One-command setup)

Needs Docker Desktop (running, ≥ 6 GB RAM), JDK 17+ and Python 3.8+. From PowerShell:

```powershell
git clone <repo-url> mini-leetcode
cd mini-leetcode
.\setup-demo.ps1
```

The script automatically executes the end-to-end setup workflow:
1. Verifies prerequisites (Docker Desktop, Java 17+, Python 3.8+, `curl`).
2. Generates `.env` from `.env.example` if not present.
3. Packages backend JARs (`./mvnw clean package -DskipTests`).
4. Boots the Docker Compose stack (`docker compose up -d --build`).
5. Pulls sandbox runner images (`eclipse-temurin:17-jdk`, `python:3.12-slim`).
6. Awaits backend API health probe (`UP`).
7. Populates problems, grading rules, contests, Mini Bank OOP project, and test cases via REST API.
8. Loads demo accounts and seeds submissions.

Safe to run again; `.\setup-demo.ps1 -SkipBuild` skips the Maven/Docker build and only reloads seed and demo data.

**Pre-seeded Demo Accounts:**
- **ADMIN:** `admin` / `Password123!` (or value configured in `.env`)
- **INSTRUCTOR:** `demo_instructor` / `Demo@123456`
- **STUDENT:** `demo_student` / `Password123!`

---

## 6. Quick start (Docker — recommended)

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| Docker Desktop | recent; give it **≥ 6 GB RAM** (Settings › Resources) | the whole stack + sandbox containers |
| JDK | 17 LTS | building the backend JARs |
| Node.js | 20+ | frontend dev mode and e2e tests (optional for Docker-only use) |
| JDK | 21 | the IntelliJ plugin (toolchain target) |
| Python | 3.10+ | problem content pipeline and test case generation |

> [!IMPORTANT]
> With the default 2 GB Docker Desktop memory the engine can be killed while the stack and sandboxes run; ensure it is set to **≥ 6 GB RAM** before starting.

### Step 1 — Build the backend JARs

The API and judge images copy prebuilt JARs, so build them first (from the repository root):

```bash
cd backend
./mvnw clean package -DskipTests    # quick build; omit -DskipTests for full unit+integration test suite (~8 min)
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
BOOTSTRAP_ADMIN_PASSWORD=Password123!
```

Then:

```bash
docker compose up -d --build
```

| Service | Internal Port | Default Host URL | Remapped Host URL (override) |
|---|---|---|---|
| **Web application** | `3000` | <http://localhost:3100> | <http://localhost:3100> |
| **Backend API / Swagger UI** | `8080` | <http://localhost:8080/swagger-ui/index.html> | <http://localhost:8088/swagger-ui/index.html> |
| **API Actuator Health** | `8080` | <http://localhost:8080/actuator/health> | <http://localhost:8088/actuator/health> |
| **MinIO Console** | `9001` | <http://localhost:9001> (`minioadmin` / `minioadminpassword`) | <http://localhost:9001> |
| **MinIO S3 API** | `9000` | <http://localhost:9000> | <http://localhost:9000> |
| **MySQL Database** | `3306` | `localhost:3306` | `localhost:3307` (`leetcode / leetcode_password`) |
| **Redis Streams Queue** | `6379` | `localhost:6379` | `localhost:6379` |

> [!NOTE]
> **Host port already in use?** Create `docker-compose.override.yml` to remap host ports, e.g. when local MySQL (3306) or 8080 is occupied:
> ```yaml
> services:
>   mysql:
>     ports: !override
>       - "3307:3306"
>   mini-leetcode-api:
>     ports: !override
>       - "8088:8080"
> ```
> The Next.js web application proxies all `/api/*` requests over Docker internal network (`http://mini-leetcode-api:8080`), so remapping the host port does not break client-to-backend communication.

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

### Step 6 — The IntelliJ IDEA plugin

The IntelliJ plugin bridges Study Plans, multi-stage OOP projects, and automated grading directly into the student's IDE without file clutter.

1. **Build & Package:**
   ```bash
   cd ide-plugin
   ./gradlew buildPlugin     # requires JDK 21 toolchain
   cd ..
   ```
   The distribution ZIP is generated at `ide-plugin/build/distributions/mini-leetcode-intellij-0.1.0.zip` (and served directly from `/ide` on the web interface or `mini-leetcode-intellij.zip` in repository root).

2. **Install in IntelliJ IDEA:**
   - In IntelliJ IDEA (2025.3+), go to **Settings (Preferences) › Plugins › ⚙ › Install Plugin from Disk...**.
   - Select `mini-leetcode-intellij.zip` and restart the IDE if prompted.

3. **Configure Authentication:**
   - Navigate to **Settings › Tools › Mini LeetCode**.
   - Set **Server URL** to your API endpoint (e.g., `http://localhost:8088` or `http://localhost:3100`).
   - Enter a **Personal Access Token (PAT)**:
     - Generate a token from the web app (`/settings/tokens` or via `POST /api/v1/me/tokens`).
     - Or use the 1-click **"Open in IntelliJ"** button on the web platform, which automatically pairs the token via the plugin's built-in Netty HTTP bridge (`http://localhost:63342/api/mini-leetcode`).
   - Tokens are stored securely using IntelliJ Platform `PasswordSafe`.

4. **In-IDE Solving Workflow:**
   - Open the **Mini LeetCode** Tool Window on the right edge of IntelliJ.
   - **Tab 1 (Study Plans):** Browse course projects (e.g. *OOP in Java: Mini Bank*), inspect stage prerequisites, and open stages into clean Maven projects.
   - **Tab 2 (Problem View):** Read the HTML-rendered problem statement, click **Run** to execute sample tests, or click **Submit** to trigger full grading. Upon `Accepted`, click **Next Stage →** to smoothly advance to the next project phase.
   - **Run in sandbox for plugin development:**
     ```bash
     cd ide-plugin && ./gradlew runIde
     ```

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

| Suite | Command | Result |
|---|---|---|
| **Backend (unit + integration)** | `cd backend && ./mvnw test` | **955 passed**, 0 failed, 7 skipped (common 6 · core 234 · api 312 · judge 403) |
| **Frontend type check / lint** | `cd frontend && npx tsc --noEmit && npm run lint` | clean (zero errors or warnings) |
| **Frontend e2e** | `cd frontend && npm run test:e2e` (or `BASE_URL=http://localhost:3100 npx playwright test`) | **165 passed** (comprehensive full-flow and anti-cheat exam coverage) |
| **IntelliJ plugin** | `cd ide-plugin && ./gradlew test` | **404 passed** (58 test suites covering API client, bridge, tool windows, run/submit handlers, tree models, password safe) |

---

## 9. API documentation, Security & Observability

### Interactive Documentation & Health Probes

- **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) *(or port `8088` if remapped)*
- **OpenAPI 3.1 JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Spring Boot Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **MinIO Object Storage Console:** [http://localhost:9001](http://localhost:9001) (`minioadmin` / `minioadminpassword`)

### Authentication & Token Architecture

The platform strictly segregates user sessions and developer automation:

1. **Web Sessions (Spring Security JWT):**
   - Issued via `POST /api/v1/auth/login` and refreshed via `POST /api/v1/auth/refresh`.
   - Grants full access according to role (`ADMIN`, `INSTRUCTOR`, `USER`).

2. **Personal Access Tokens (PAT):**
   - Generated via `POST /api/v1/me/tokens` for IntelliJ IDEA plugin authentication.
   - Raw tokens (`mlc_pat_*`) are presented once at creation; only their SHA-256 hashes are stored in the database.
   - **PAT Allowlist Enforced:** To uphold exam integrity, PAT credentials cannot access exam/contest submissions. They are restricted strictly to study plans, problem specifications, and practice evaluations (`POST /api/v1/problems/*/run-multipart`, `POST /api/v1/problems/*/submit-multipart`).

---

## 10. Further documentation

- [Developer onboarding guide](docs/ONBOARDING.md)
- [Project architecture & plan](docs/PROJECT.md)
- [Business requirements](docs/BUSINESS_REQUIREMENTS.md) · [Technical & business documentation](docs/TECHNICAL_AND_BUSINESS_DOCUMENTATION.md)
- [Exam anti-cheat specification](docs/EXAM_ANTI_CHEAT_SPECIFICATION.md) · [Hybrid submission specification](docs/HYBRID_SUBMISSION_SPECIFICATION.md)
- [OOP grinding engine handoff](docs/OOP_GRINDING_HANDOFF.md) · [Test infrastructure](docs/TEST_INFRA.md)
- Study plans, topics & plugin: [interactive topic exercises](docs/INTERACTIVE_TOPICS_AND_EXERCISES.md), [study-plan](specs/study-plan.spec.md), [topics spec](specs/topics.spec.md), [course builder](specs/course-builder.spec.md), [plugin in-IDE solving](specs/plugin-in-ide-solving.spec.md)
- Admin: [roadmap](specs/admin-roadmap.md), [contests](specs/admin-contest-management.spec.md), [user management](specs/admin-user-management.spec.md)
- Judge: [live terminal console](specs/live-terminal-console.spec.md), [OOP runtime metrics](specs/oop-console-runtime-metrics.spec.md)
- [Authoring problems, testcases and OOP grading](backend/tools/README.md) · [Frontend design system](frontend/DESIGN.md)
