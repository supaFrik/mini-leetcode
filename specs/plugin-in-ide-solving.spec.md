# Specification: In-IDE Problem Solving & Verification (Study Plan Plugin)

> **Status:** APPROVED (Finalized via `/grill-me` & `/feature-forge` interview on 2026-11-20)  
> **Supersedes:** `specs/plugin-in-ide-solving.spec.md` Draft v1, and modifies PL-4 & PL-6 in `specs/study-plan.spec.md`.  
> **Related Components:** `ide-plugin`, `backend/mini-leetcode-api`, `JwtAuthenticationFilter`, `IdeBridgeService`.

---

## 1. Overview & User Value

### 1.1 Context & Current Limitations
Previously, opening a Study Plan stage in the IntelliJ plugin downloaded a new standalone Maven project and generated a Markdown file at `stages/stage-N.md`. The user had to read the problem using IntelliJ's built-in Markdown preview, which suffered from layout breakages (e.g., malformed tables like `CommandOutput DEPOSITNew balance...`). Furthermore:
- The check action was tucked away in editor popup menus or the main toolbar, disconnected from the problem description.
- Results were communicated only via brief floating balloon notifications without detailed diffs or testcase diagnostics.
- There was no lightweight **Run** mechanism to verify sample test cases without making an official submission.
- Multiple `.md` files cluttered the student's source repository.

### 1.2 Target Experience
The entire learning loop—**Read Problem Statement → Code → Run Sample Tests → Submit for Official Evaluation → Unlock Next Stage**—takes place within a dedicated **Tabbed Tool Window** inside IntelliJ IDEA:
1. **Tab 1: Study Plans** — Interactive tree hierarchy of Study Plans, Items, and Stages with live status icons (`LOCKED`, `NEVER`, `IN_PROGRESS`, `DONE`).
2. **Tab 2: Problem View** — Rich HTML-rendered problem description, action toolbar with **Run** and **Submit** buttons, an expandable/collapsible **Result Panel** showing real-time judging progress, verdict, runtime, and failure diffs, plus a **Next Stage →** button upon acceptance.
3. **Clean Workspace** — Zero `stages/*.md` files written to the project repository. All source code resides cleanly in `src/main/java`.

### 1.3 User Value
- **Students:** Frictionless developer experience inside their real IDE. Instant feedback on public testcases with **Run**, full grading with **Submit**, and instant progression to the next stage within the exact same project without code loss.
- **Instructors / Platform:** Consistent presentation matching the web platform; zero out-of-sync problem descriptions in local student files.

---

## 2. Settled Decisions (Grilling Session Summary)

| Decision ID | Topic | Decision |
|---|---|---|
| **D1** | **Tool Window Layout** | **Tabbed Tool Window**: Tab 1 = "Study Plans" (Tree syllabus), Tab 2 = "Problem View" (Header, Run/Submit buttons, Collapsible Result section, Rendered Statement). |
| **D2** | **Project & Tab Synchronization** | **Flexible binding**: Selecting any unlocked stage in Tab 1 loads its statement into Tab 2. When a Mini LeetCode project is active in the IDE, Tab 2 auto-loads the active stage from `.mlc/project.yaml`. Run/Submit buttons are enabled only when the open project matches the viewed stage. |
| **D3** | **Result Section Placement** | **Collapsible Result Section**: Positioned directly beneath the Action Toolbar and above the Problem Statement. Expands automatically upon Run/Submit, displaying "Evaluating..." progress, verdict badge, execution metrics, and failure diffs. Can be collapsed to read the statement. |
| **D4** | **Problem Statement Source** | **Zero `.md` files in project**: `WorkspaceService` completely ceases writing `stages/stage-N.md`. Problem description is fetched via API and rendered directly in Tab 2. |
| **D5** | **Run vs Submit Semantics** | **Run**: Executes public/sample test cases via `POST /api/v1/problems/{id}/run-multipart` (`isQuickRun=true`). Does NOT complete stage.<br/>**Submit**: Evaluates all hidden/system test cases via `POST /api/v1/problems/{id}/submit-multipart`. On `AC`, marks stage `DONE` and unlocks next stage. |
| **D6** | **Next Stage Progression** | **Auto-unlock & Same Project**: Upon `AC`, the progress tree in Tab 1 auto-refreshes, and a prominent **"Next Stage →"** button appears in Tab 2. Clicking it adds new starter files without overwriting student code, updates `.mlc/project.yaml`, and switches Tab 2 to the new stage. |
| **D7** | **Auto-save on Action** | Clicking Run or Submit automatically calls `FileDocumentManager.getInstance().saveAllDocuments()` before archiving `src/main/java`. |
| **D8** | **Backend PAT Allowlist** | Add `POST /api/v1/problems/*/run` and `POST /api/v1/problems/*/run-multipart` to `PAT_ALLOWLIST` in `JwtAuthenticationFilter`. |

---

## 3. Architecture & Component Interaction

```
+-----------------------------------------------------------------------------------+
| IntelliJ Tool Window: "Mini LeetCode"                                             |
| +-------------------------------------------------------------------------------+ |
| | Tab 1: Study Plans                       | Tab 2: Problem View                | |
| +-------------------------------------------------------------------------------+ |
| | [Tree: Plan -> Item -> Stage]            | Header: Stage 1 · Title [Easy]     | |
| |  * Item 1: Mini Bank Console             | Actions: [Run] [Submit] [Next ->]  | |
| |    - [v] Stage 1 (Done)                  | ---------------------------------- | |
| |    - [o] Stage 2 (In Progress)           | [Collapsible Result Panel]         | |
| |    - [x] Stage 3 (Locked)                |   Status: Accepted (3/3) · 42ms    | |
| |                                          | ---------------------------------- | |
| | Toolbar: [Open Stage] [Refresh]          | [Rendered Problem Statement]       | |
| |                                          |   HTML / Markdown Render (JCEF /   | |
| |                                          |   JBHtmlPane fallback)             | |
| +------------------------------------------+------------------------------------+ |
+-----------------------------------------------------------------------------------+
```

### Component Breakdown
1. **`MiniLeetCodeToolWindowFactory`**: Registers a `JBTabbedPane` or `ContentManager` with two tabs: "Study Plans" and "Problem".
2. **`StudyPlanPanel`** (Tab 1): Contains the existing `StudyPlanTreeModel` and toolbar (`OpenStageAction`, `RefreshPlansAction`). Selecting a stage sends an event to `ProblemViewPanel`.
3. **`ProblemViewPanel`** (Tab 2):
   - **`ProblemHeaderBar`**: Title, difficulty badge, status tag.
   - **`ProblemActionBar`**: `RunAction` button, `SubmitAction` button, and `NextStageAction` button.
   - **`ResultSectionPanel`**: Collapsible container showing judging status, verdict badge, execution time/memory, testcase pass ratio (`passTestCases / totalTestCases`), and formatted error/diff.
   - **`StatementViewerPanel`**: HTML renderer using `JBCefBrowser` if supported, falling back to `JBHtmlPane` / `JEditorPane`. Includes theme-aware CSS (dark/light adaptation).
4. **`RunHandler` & `SubmitHandler`**:
   - Saves all open editor documents.
   - Packages `src/main/java/**.java` into a ZIP archive in memory.
   - Dispatches `runMultipart` or `submitStage` via `ApiClient`.
   - Polls `GET /api/v1/submissions/{id}` at 1-second intervals (up to 120s timeout).
   - Updates `ResultSectionPanel` on EDT.

---

## 4. Functional Requirements (EARS Format)

### 4.1 Problem Viewing (Statement & Tabs)
- **FR-PV-01**: When a user selects a stage node in the Study Plans tree (Tab 1), the system **shall** load the problem statement and details into the Problem View (Tab 2) within 1.0 second of API response.
- **FR-PV-02**: Where a Mini LeetCode project is open in the active IDE window, the system **shall** automatically detect its `.mlc/project.yaml` and initialize Tab 2 with the project's current stage upon tool window opening.
- **FR-PV-03**: The system **shall** render Markdown elements—including GFM tables, fenced code blocks with monospace font, inline code, bold/italic text, and bullet lists—with proper formatting and theme-aware contrast.
- **FR-PV-04**: The system **shall not** create or write any `stages/stage-N.md` file to the student's project workspace.
- **FR-PV-05**: Where a stage has status `LOCKED`, the system **shall** display a locked placeholder message (*"Complete previous stages to unlock this stage"*) in Tab 2 and disable the Run and Submit actions.
- **FR-PV-06**: Where a stage is unpublished (`published = false`), the system **shall** display a maintenance notice (*"Stage is currently being updated"*) and disable Run and Submit actions.

### 4.2 Run Sample Tests
- **FR-RN-01**: When the user clicks **Run** (or triggers shortcut `Ctrl+Alt+R`), the system **shall** save all modified documents in the IDE, compress `src/main/java/**/*.java` into an in-memory ZIP, and transmit it to `POST /api/v1/problems/{id}/run-multipart`.
- **FR-RN-02**: While the run request is evaluating, the system **shall** expand the Result Section, display an animated or indeterminate *"Evaluating sample test cases..."* progress state, and disable Run and Submit buttons to prevent concurrent requests.
- **FR-RN-03**: When the run execution finishes, the system **shall** display the verdict, sample test cases passed count (`X/Y`), runtime in milliseconds, and the first failing test case input/output diff if applicable.
- **FR-RN-04**: The system **shall not** update the student's study plan progress or mark a stage as `DONE` from a **Run** execution.

### 4.3 Submit & Stage Completion
- **FR-SB-01**: When the user clicks **Submit** (or triggers shortcut `Ctrl+Alt+Enter`), the system **shall** save all modified documents, package `src/main/java/**/*.java`, and transmit to `POST /api/v1/problems/{id}/submit-multipart`.
- **FR-SB-02**: While judging, the system **shall** expand the Result Section, display *"Submitting and judging all test cases..."*, and disable action buttons.
- **FR-SB-03**: When the submission verdict is `AC` (Accepted), the system **shall** display a success banner with score and metrics, trigger a background refresh of the Study Plan tree to mark the stage `DONE`, and display the **Next Stage →** button in Tab 2.
- **FR-SB-04**: When the submission verdict is not `AC` (e.g., `WA`, `TLE`, `CE`, `RE`), the system **shall** display the specific failure verdict, passed/total testcase ratio, execution phase, and the compiler or error diagnostic message.

### 4.4 Next Stage Transition
- **FR-NS-01**: When the user clicks **Next Stage →**, the system **shall** query the next sequential stage within the current study plan item.
- **FR-NS-02**: When transitioning to the next stage, the system **shall** download the starter template for the next stage and extract ONLY files that do not currently exist in the local project, strictly preserving existing student files.
- **FR-NS-03**: The system **shall** update `.mlc/project.yaml` with the new stage number and problem slug, notify the user of newly added files, and update Tab 2 with the new stage's statement.

---

## 5. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| **NFR-01** | **Performance** | Statement rendering must complete in $\le 200\text{ms}$ after network response. UI operations must never block the Swing Event Dispatch Thread (EDT). |
| **NFR-02** | **Polling & Timers** | Result polling shall occur every 1,000ms up to a maximum duration of 120 seconds. If timed out, show a timeout notification and link to web submissions. |
| **NFR-03** | **Security & Auth** | Personal Access Token (PAT) must be retrieved securely from `MiniLeetCodePasswordSafe`. Backend `JwtAuthenticationFilter` must strictly enforce allowlisted endpoints. |
| **NFR-04** | **Data Safety** | No student source file under `src/main/java` shall ever be overwritten or truncated during stage transitions. All file additions must run under `WriteCommandAction`. |
| **NFR-05** | **Platform Compatibility** | Target IntelliJ IDEA Community and Ultimate $\ge 2024.2$ / $2025.3+$ (`sinceBuild = 253`), JDK 21 runtime. Fallback gracefully if `JBCefApp.isSupported() == false`. |
| **NFR-06** | **Theme Adaptation** | Problem View and Result Panel must dynamically adapt text color, background, and code block styling to IntelliJ's active LaF (Light / Darcula / New UI Dark). |

---

## 6. Acceptance Criteria (Given / When / Then)

### Scenario 1: Tabbed Interface and Problem Browsing
- **Given** a user has configured a valid PAT and opened the "Mini LeetCode" Tool Window,
- **When** the Tool Window initializes,
- **Then** two tabs appear: "Study Plans" and "Problem View".
- **And** when clicking a stage in Tab 1, Tab 2 immediately displays the problem title, difficulty badge, and rendered statement with formatted tables and code blocks.
- **And** no `stages/*.md` file is created in the project directory.

### Scenario 2: Quick Run on Public Samples
- **Given** an open Mini LeetCode project matching the active stage in Tab 2,
- **When** the user clicks the "Run" button,
- **Then** all open editor files are automatically saved,
- **And** the Result Section expands displaying *"Evaluating sample test cases..."*,
- **And** upon server completion, it displays the verdict (e.g. `Wrong Answer` with `1/2` sample testcases passed, or `Accepted` `2/2`),
- **And** the stage status in Tab 1 remains unchanged (`IN_PROGRESS`).

### Scenario 3: Official Submit and Stage Acceptance
- **Given** a student has completed the correct implementation for Stage 1,
- **When** the user clicks "Submit",
- **Then** the Result Section displays *"Submitting and judging..."*,
- **And** within judging time + 1s, the Result Section displays `Accepted · 3/3 test cases passed`,
- **And** Tab 1 automatically updates Stage 1 with a green checkmark `DONE`,
- **And** Stage 2 in Tab 1 transitions from `LOCKED` to `IN_PROGRESS`,
- **And** a prominent "Next Stage →" button becomes visible in Tab 2's action bar.

### Scenario 4: Next Stage within the Same Project
- **Given** Stage 1 is marked `DONE` and `Account.java` was modified by the student,
- **When** the user clicks "Next Stage →",
- **Then** Tab 2 switches its statement to Stage 2,
- **And** existing `Account.java` is preserved intact with zero modifications,
- **And** any new starter files (e.g. `SavingsAccount.java`) are added to `src/main/java`,
- **And** `.mlc/project.yaml` reflects `stage: 2`.

### Scenario 5: Mismatched Project Guard
- **Given** the user has project `mini-bank-console` open,
- **When** the user browses to a different problem or stage in Tab 1,
- **Then** Tab 2 displays the problem statement for reading,
- **But** the "Run" and "Submit" buttons are disabled with a tooltip: *"Project does not match this stage. Open this stage to enable execution."*

---

## 7. Error Handling & Edge Cases

| Error Scenario | Root Cause / Trigger | Plugin Behavior |
|---|---|---|
| **Expired / Invalid PAT** | HTTP 401 `PAT_INVALID` from server | Displays banner in Result Section: *"Session expired. Generate a new token on web."* with a button to open Settings. |
| **Forbidden Endpoint** | HTTP 403 `PAT_FORBIDDEN` | Displays message: *"This action is restricted to the web platform."* |
| **Locked Stage Action** | HTTP 409 `STAGE_LOCKED` | Reverts button states, displays warning: *"Stage is locked. Complete previous stages first."* |
| **No Java Files Found** | `src/main/java` has no `.java` files | Blocks network call, displays warning: *"No Java source files found in src/main/java."* |
| **Compilation Error** | Verdict `CE` | Result Section highlights `Compilation Error` and displays the formatted compiler diagnostic output (truncated to first 25 lines if long). |
| **Judging Timeout** | Polling exceeds 120s | Shows warning: *"Judging took longer than 120s. Check submission history on the web."* Re-enables buttons. |
| **JCEF Unavailable** | Headless or embedded OS environment without Chromium | Gracefully falls back to `JBHtmlPane` / `JEditorPane` with standard Swing styling. |

---

## 8. Implementation Checklist & Slices

### Slice A — Backend PAT Allowlist & Verification
- [ ] Add `POST /api/v1/problems/*/run` and `POST /api/v1/problems/*/run-multipart` to `PAT_ALLOWLIST` in `JwtAuthenticationFilter.java`.
- [ ] Verify that PAT authentication succeeds for `run-multipart` and rejects unauthorized non-allowlisted routes with 403.

### Slice B — Plugin API Client Enhancement
- [ ] Add `runStage(long problemId, byte[] zipBytes)` in `ApiClient.java` invoking `POST /api/v1/problems/{id}/run-multipart`.
- [ ] Add `getProblemSubmissions(long problemId)` in `ApiClient.java` (if needed for submission history).
- [ ] Add unit tests for `runStage` using `MockWebServer`.

### Slice C — Tool Window Restructuring (Tabbed Interface)
- [ ] Update `MiniLeetCodeToolWindowFactory.java` to construct a `JBTabbedPane` with:
  - Tab 1: "Study Plans" (existing `StudyPlanTreeModel` + toolbar).
  - Tab 2: "Problem View" (`ProblemViewPanel`).
- [ ] Implement stage selection listener in Tab 1 that triggers loading in Tab 2.
- [ ] Detect open project `.mlc/project.yaml` on project opening and auto-load active stage in Tab 2.

### Slice D — Problem View Panel & Markdown Renderer
- [ ] Implement `ProblemViewPanel` layout:
  - Header: Title, difficulty tag, problem slug.
  - Action Bar: `Run` button, `Submit` button, `Next Stage` button.
  - Result Section: Collapsible card with status, verdict badge, execution metrics, and error diagnostics.
  - Statement Container: Markdown-to-HTML viewer using `JBCefBrowser` with fallback to `JBHtmlPane`.
- [ ] Integrate clean theme-adaptive CSS (dark/light mode colors matching IDE editor).
- [ ] Cease generating `stages/stage-N.md` in `WorkspaceService.java`.

### Slice E — Run & Submit Handlers & Next Stage Integration
- [ ] Implement `RunActionHandler` and `SubmitActionHandler`:
  - Call `FileDocumentManager.getInstance().saveAllDocuments()`.
  - Validate and archive `src/main/java/**.java`.
  - Update Result Section to "Evaluating..." / "Submitting...".
  - Poll `GET /api/v1/submissions/{id}` and display final verdict and diagnostics.
  - On `AC` for Submit: auto-refresh Study Plan tree, mark stage `DONE`, and reveal `Next Stage →` button.
- [ ] Wire `NextStageAction` into the Problem View Action Bar.

### Slice F — Testing & Verification
- [ ] Unit tests for `ProblemViewPanel` state transitions (Locked, Evaluating, Accepted, Error).
- [ ] Unit tests for `ApiClient` run/submit endpoints.
- [ ] End-to-end verification of the full solve-and-pass cycle.
