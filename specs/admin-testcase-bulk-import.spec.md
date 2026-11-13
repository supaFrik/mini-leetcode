# Feature: Nhập testcase hàng loạt từ ZIP

## Overview

Testcase hiện chỉ thêm được từng cái (giao diện admin hoặc script PowerShell gọi API). Tính năng cho phép
giảng viên **upload một file ZIP** theo chuẩn phổ biến `1.in / 1.out, 2.in / 2.out, …` (như Codeforces/Polygon)
để tạo nhiều testcase một lần, chọn **thêm tiếp** hoặc **thay toàn bộ** bộ testcase cũ.

Giá trị: đưa một bài có 30–50 testcase lên hệ thống trong một thao tác; dùng lại bộ test có sẵn từ nguồn khác.

## Quyết định đã chốt

| # | Quyết định | Chọn |
|---|-----------|------|
| D1 | Định dạng | ZIP chứa cặp `<tên>.in` + `<tên>.out` (chấp nhận thêm `.ans` thay `.out`, `input/`+`output/` thư mục con thì **không**) |
| D2 | Thứ tự | Sắp theo tên tự nhiên (`2` trước `10`) |
| D3 | Public/hidden | Tuỳ chọn "N case đầu là public" (mặc định 1), còn lại hidden |
| D4 | Bài đã có testcase | Người dùng chọn **Append** hoặc **Replace all**; Replace là một giao dịch |
| D5 | Bài PUBLISHED | Bị khoá như mọi thao tác testcase khác (409) |
| D6 | Export ZIP, nhập từ nhiều input + lời giải mẫu | **Ngoài phạm vi** lần này |

## Functional Requirements

- **FR-TI-001**: When staff uploads a ZIP to `POST /api/v1/problems/{id}/test-cases/import` with `mode=APPEND|REPLACE` and `publicCount`, the system shall parse it, validate every entry, and only then write anything.
- **FR-TI-002**: The system shall pair files by base name: `X.in` with `X.out` (or `X.ans`); names may contain letters, digits, `-`, `_`; files in the root or in a single top-level folder are accepted.
- **FR-TI-003**: The system shall reject the whole archive (400, nothing written) if any `.in` lacks a matching output or vice versa, listing every unmatched name.
- **FR-TI-004**: The system shall ignore OS metadata (`__MACOSX/`, `.DS_Store`, `Thumbs.db`) and reject other unexpected files with a list of their names.
- **FR-TI-005**: The system shall order cases by natural sort of the base name and, in APPEND mode, place them after the existing cases.
- **FR-TI-006**: The system shall mark the first `publicCount` imported cases public and the rest hidden; `publicCount` is clamped to the number of imported cases.
- **FR-TI-007**: Where `mode=REPLACE`, the system shall delete the old cases and create the new ones in one database transaction; storage objects of old cases are deleted only after commit, and new objects are deleted if the transaction fails (same pattern as `TestCaseService.updateTestCase`).
- **FR-TI-008**: The system shall normalize CRLF to LF in `.in`/`.out` contents only when the option "Normalize line endings" is on (default on), and shall reject empty `.in` files (consistent with single-case upload); empty `.out` is allowed only if the option "Allow empty output" is on.
- **FR-TI-009**: When the import succeeds, the system shall return the created cases (ids, order, public flag, sizes) and the UI shall refresh the Test cases tab and offer "Check against reference" if a reference solution exists.
- **FR-TI-010**: The UI shall show a preview before upload (client-side read of the ZIP listing): number of pairs, detected problems (unmatched files), total size, and the chosen mode; Replace all requires typing the problem title or a confirm dialog naming how many cases will be removed.

## Non-Functional Requirements

### Security (ZIP handling — reuse `SourcePathValidator` rules)
- Reject path traversal (`..`, absolute paths, drive letters), symlinks, nested archives.
- Limits: ≤ 200 pairs, each file ≤ 16 MB, total uncompressed ≤ 100 MB, compression ratio guard (zip-bomb) > 100:1 per entry rejected; upload size limit raised only for this endpoint.
- Content type is not trusted; entries are read as bytes.
- `hasAnyRole('INSTRUCTOR','ADMIN')` (`SecurityConfig` POST `/api/v1/problems/*/test-cases/**` already covers it) + `@PreAuthorize`.

### Performance
- 50 pairs / 5 MB imported in ≤ 5 s (streaming entries straight to MinIO, no full in-memory unzip).

### Reliability
- All-or-nothing: either every case is created (and in REPLACE, old ones removed) or the problem's test cases are unchanged.

## Acceptance Criteria

### AC-TI-001: Nhập mới
Given a DRAFT problem with no test cases
When staff uploads `tests.zip` with 1.in/1.out … 12.in/12.out, publicCount 2, mode APPEND
Then 12 cases are created in order 1..12, cases 1–2 public, 3–12 hidden.

### AC-TI-002: Thiếu file thì không ghi gì
Given a ZIP with 5.in but no 5.out
When uploaded
Then the response is 400 listing "5.in has no matching .out" and the problem's cases are unchanged.

### AC-TI-003: Thay toàn bộ an toàn
Given a problem with 10 cases
When staff imports 8 cases with REPLACE and MinIO fails while uploading case 6
Then the response is an error and the problem still has its original 10 cases.

### AC-TI-004: Chặn ZIP độc hại
Given a ZIP containing `../../etc/passwd.in` or a 1 KB entry expanding to 1 GB
When uploaded
Then it is rejected with 400 and nothing is written.

### AC-TI-005: Bài đã Publish
Given a PUBLISHED problem
When staff opens Import
Then the button is disabled with the lock notice, and the API returns 409.

## Error Handling

| Error Condition | HTTP | Message |
|-----------------|------|---------|
| Not a ZIP / corrupt | 400 | "File is not a valid ZIP archive" |
| Unmatched files | 400 | "Unmatched files: 5.in, 7.out" |
| Unexpected files / unsafe paths | 400 | "Unsupported entries: …" |
| Empty input file | 400 | "3.in is empty" |
| Limits exceeded / zip bomb | 413 | "Archive exceeds limits (max 200 pairs, 100 MB)" |
| Problem PUBLISHED | 409 | "Test cases of a PUBLISHED problem are frozen" |
| Storage failure | 500 | "Import failed, no test cases were changed" |

## Implementation TODO

### Backend
- [ ] `TestCaseImportService`: streaming ZIP parse, validation pass, write pass, transactional REPLACE with after-commit cleanup
- [ ] Natural sort comparator, base-name pairing, metadata ignore list
- [ ] Endpoint `POST /api/v1/problems/{id}/test-cases/import` (multipart: `file`, `mode`, `publicCount`, `normalizeLineEndings`, `allowEmptyOutput`)
- [ ] Endpoint-specific multipart size limit

### Frontend
- [ ] "Import ZIP" button in Test cases tab → dialog: file picker, client-side preview (JSZip listing), options, mode
- [ ] Replace-all confirmation naming the number of removed cases
- [ ] Result summary + refresh list

### Testing
- [ ] Unit: pairing, natural sort, traversal/zip-bomb rejection, limits
- [ ] Integration: APPEND/REPLACE, rollback on storage failure, PUBLISHED 409
- [ ] E2E: upload 12-case ZIP and see them listed

## Out of Scope
- Export ZIP
- Generating outputs from many inputs with the reference solution (can follow later)
- `input/` + `output/` folder layout, `.txt` naming schemes
