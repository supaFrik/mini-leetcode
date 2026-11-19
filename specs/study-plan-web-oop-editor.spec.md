# Specification: Study Plan Web Multi-File OOP Code Editor

## Overview and User Value
Currently, when a learner accesses an OOP (multi-file) problem inside a Study Plan stage (`/study-plans/[slug]/[itemId]/[stageNo]`), the platform only displays a prompt requiring them to open IntelliJ IDEA, or redirects them to a complex 3-panel workspace at `/problems/[slug]`.

This feature provides a lightweight, integrated multi-file Java code editor directly within the Study Plan stage view. Learners can write, edit, add, or delete `.java` files, run their solution against sample testcases, and submit their solution for comprehensive grading (both functional tests and strict OOP structural rules) without having to launch an external IDE. The vertical stage layout is preserved, and the "Connect to IntelliJ" option remains available as a secondary alternative.

---

## User Stories
- **US-1 (Learner - Web Solving)**: As a learner working on a Study Plan stage, I want to edit multi-file Java code directly on the web page so that I can learn and submit solutions quickly without launching an IDE.
- **US-2 (Learner - Multi-File Tabs)**: As a learner, I want to switch between starter files (e.g. `Main.java`, `Shape.java`), add new `.java` files, or delete extra files so that I can structure my OOP solution properly.
- **US-3 (Learner - Testing & Feedback)**: As a learner, I want to run my code against sample tests and submit for grading against test cases and OOP rules, receiving immediate, clean feedback in place.
- **US-4 (Learner - Progress Synchronization)**: As a learner, when my submission receives an `AC` verdict, the stage status should update to `DONE` and unlock the next stage.
- **US-5 (Learner - Optional IDE Integration)**: As a learner, I want a visible "Connect to IntelliJ" button so that I can still choose to open the project in IntelliJ IDEA when I prefer working in a full desktop IDE.

---

## Functional Requirements (EARS Format)

### Multi-File Editor & Tabs
- **FR-1**: When an OOP problem is loaded in `StageView`, the system shall render a multi-file tab bar containing tabs for all starter `.java` files defined in `problem.starterTemplates`.
- **FR-2**: When the learner clicks a file tab, the editor shall switch the active file view to that file's content while preserving edits in all other files.
- **FR-3**: When the learner clicks the "+ Add Java file" button, the system shall prompt for a file name, validate that it ends with `.java` and does not duplicate an existing file name, and add a new tab initialized with a basic class skeleton.
- **FR-4**: When the learner clicks the remove (`x`) button on a non-primary file tab and confirms deletion, the system shall remove the file and switch active focus to an adjacent tab. The system shall forbid deleting the only remaining file.
- **FR-5**: When the learner clicks "Reset code" and confirms, the system shall restore all files and contents back to the original starter template configuration.

### Local Draft Persistence
- **FR-6**: While the learner edits any file, the system shall automatically persist the entire array of files to browser `localStorage` keyed by `study-plan-draft:${problemId}`.
- **FR-7**: When the learner reloads or revisits the stage, the system shall restore the saved draft if present, or fall back to `starterTemplates`.

### Execution, Grading & Feedback
- **FR-8**: When the learner clicks "Run", the system shall submit the current multi-file set with `run: true` (evaluating against public sample testcases) and display test results inline without marking the stage complete.
- **FR-9**: When the learner clicks "Submit", the system shall submit the full multi-file set to the judge engine for official grading against all testcases and OOP reflection/bytecode inspection rules.
- **FR-10**: While judging is in progress, the system shall disable the Run/Submit buttons, show a loading spinner, and poll the submission status until completion.
- **FR-11**: When the submission evaluation completes with `verdict === "AC"`, the system shall display a green "Correct!" verdict banner and invoke `onSolved()` to refresh the study plan progress and unlock subsequent stages.
- **FR-12**: When the submission evaluation fails (`WA`, `CE`, `RE`, `SV`), the system shall display a minimalist verdict box showing the failure reason, score (if partial credit), compilation error log or OOP rule violation message, and the diff for the first failing test case.

### IDE Coexistence
- **FR-13**: Where an OOP stage is active, the system shall display a compact "Connect to IntelliJ" button toolbar above or below the editor, allowing learners to launch IntelliJ IDEA via `IdeButton` if preferred.

---

## Non-Functional Requirements
- **NFR-1 (UI Performance)**: Tab switching between Java files shall be instantaneous (< 50ms) without re-mounting or flickering CodeMirror.
- **NFR-2 (Clean Vertical Layout)**: The layout must remain strictly vertical (Breadcrumb -> Title -> Problem Description -> Sample IO -> Multi-File Editor & IntelliJ Button -> Result -> Topics), avoiding horizontal multi-column splits.
- **NFR-3 (Robust Fallback)**: If `problem.starterTemplates` is null or invalid JSON, the editor shall gracefully initialize with a default `Main.java` or `Solution.java`.
- **NFR-4 (Accessibility & UX)**: File tabs shall be navigable via keyboard (Enter/Space), and active files must be visually distinct with high contrast.

---

## Acceptance Criteria (Given / When / Then)

### AC-1: Multi-File Tab Navigation and Editing
- **Given** a learner is viewing an OOP stage (e.g. `shape-hierarchy`),
- **When** the page loads,
- **Then** tabs for `Main.java`, `Shape.java`, `Circle.java`, `Rectangle.java` are displayed, and selecting any tab loads its respective code into CodeMirror.

### AC-2: Adding and Removing Files
- **Given** the learner is in the multi-file editor,
- **When** the learner clicks "+ Add Java file" and inputs `Triangle.java`,
- **Then** a new tab `Triangle.java` is created with a `public class Triangle` stub, and the learner can write code in it.
- **When** the learner clicks the delete button on `Triangle.java` and confirms,
- **Then** `Triangle.java` is removed and the active tab switches to the previous tab.

### AC-3: Sample Run (Quick Test)
- **Given** the learner has written solution code,
- **When** the learner clicks the "Run" button,
- **Then** the judge executes the code against sample testcases, displaying output/diff in the inline verdict box without altering the stage's completion status.

### AC-4: Official Submit & Stage Completion
- **Given** the learner submits a completely correct OOP solution that satisfies both unit tests and OOP constraints,
- **When** the judge finishes evaluation,
- **Then** the verdict displays "Correct!", a checkmark appears, and the study plan stage is updated to `DONE`.

### AC-5: OOP Rule Violation Feedback
- **Given** the learner submits code that passes unit tests but violates OOP rules (e.g., non-private fields or missing inheritance),
- **When** evaluation completes,
- **Then** the verdict shows "Failed" or "Wrong answer" with the partial score (e.g. 60/100) and the specific OOP constraint failure in the error output.

### AC-6: IDE Button Accessibility
- **Given** the learner prefers using IntelliJ IDEA,
- **When** viewing the stage,
- **Then** the "Connect to IntelliJ" button is clearly visible alongside the web editor, allowing one-click IDE launch.

---

## Error Handling Table

| Scenario | Condition | System Action | Learner Feedback |
|---|---|---|---|
| Duplicate File Name | Learner creates a file with a name that already exists | Rejects creation | Toast: "A file with that name already exists." |
| Invalid File Name | Name empty or invalid extension | Rejects creation or appends `.java` automatically | Toast: "Please enter a valid Java class name." |
| Delete Only File | Learner attempts to delete the sole file in the workspace | Blocks action | Toast: "Cannot delete the only file in the workspace." |
| Network / Polling Error | Judge takes too long or network drops | Aborts polling gracefully | Inline error: "The checker is busy. Try again in a minute." |
| Compilation Failure (CE) | Code has Java syntax errors | Displays raw compiler output | Inline codebox with compiler error lines. |
| OOP Rule Failure | Code violates structural/reflection rules | Displays score & error message | Shows score (e.g. 60.0/100) and failed rules breakdown. |

---

## Implementation Checklist
- [ ] 1. Create `MultiFileCodeExercise` component (or enhance `CodeExercise`) with multi-file tabs, `+ Add Java file`, delete file, local draft storage, CodeMirror editor, Run & Submit actions.
- [ ] 2. Update `StageView` (`frontend/src/components/study-plans/stage-view.tsx`):
  - Replace the IntelliJ-only placeholder in `multiFile` with the embedded `MultiFileCodeExercise`.
  - Include the "Connect to IntelliJ" button (`IdeButton`) neatly alongside the editor.
- [ ] 3. Ensure two-way sync with judge API:
  - Submit `SubmissionFile[]` on Run / Submit using `useJudge`.
  - Handle result display with partial score and OOP violation message.
  - Trigger `onSolved()` on Accepted verdict.
- [ ] 4. Automated Verification:
  - E2E tests verifying tab creation, editing, running, submitting, and stage completion.
