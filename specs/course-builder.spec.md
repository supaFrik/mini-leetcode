# Course Builder — soạn study plan trên một màn hình

> **Trạng thái:** CHỐT — đang triển khai trên nhánh `feature/course-builder`.
> Thay trình soạn `/admin/study-plans/[slug]` hiện tại. Mở rộng `specs/study-plan.spec.md` và `specs/topics.spec.md`; không đổi schema.

## Vấn đề

Dựng một project 5 stage hiện cần đi qua 3 khu vực và khoảng 15 lần publish:

1. `/admin/problems`: tạo 5 đề, chép tay testcase cộng dồn, grading config, starter; mỗi đề DRAFT → REVIEWING → PUBLISHED.
2. `/admin/topics`: tạo topic, step CODE cần đề đã publish; publish từng topic.
3. `/admin/study-plans/[slug]`: tìm từng đề để thêm, gõ tìm topic cho từng stage, Save, rồi Publish.

Các lỗi UX cụ thể:

| # | Lỗi | Chỗ |
|---|-----|-----|
| U1 | Không tạo đề / topic từ trình soạn plan; rời trang thì mất thay đổi chưa lưu (không cảnh báo, không autosave) | `admin-study-plans.tsx` |
| U2 | Nút Publish công bố bản **đã lưu**; sửa chưa Save mà bấm Publish thì âm thầm công bố bản cũ | `admin-study-plans.tsx:427` |
| U3 | Plan chỉ nhận đề PUBLISHED ⇒ buộc publish đề trước khi biết nó nằm ở đâu trong khoá | `StudyPlanService.java:157` |
| U4 | Sửa testcase stage đã phát hành: tự làm PUBLISHED → ARCHIVED → DRAFT → REVIEWING → PUBLISHED ở trang khác | `ProblemLifecycle` |
| U5 | Cả plan là một form dài; sắp thứ tự chỉ bằng ↑↓; lỗi hiện từng cái một khi Save | `StudyPlanEditor` |
| U6 | Testcase cộng dồn (Q6) phải chép tay sang stage sau | quy ước soạn đề |

## Quyết định đã chốt (phỏng vấn 2026-11-13)

| # | Câu hỏi | Chốt |
|---|---------|------|
| B1 | Soạn bằng gì | **Web UI là chính**; script seed giữ cho dữ liệu mẫu |
| B2 | Bố cục | **Course builder 2 cột**: outline bên trái, chi tiết bên phải |
| B3 | Tạo stage | **Nhân bản stage trước**: đề mới chép grading, starter, lời giải mẫu, testcase (giữ nhãn) |
| B4 | Publish | **Một nút Publish** có checklist; hết lỗi thì publish plan + mọi đề / topic DRAFT bên trong |
| B5 | Sửa khi đã phát hành | Sửa thẳng, có cảnh báo số học viên; testcase / grading cần **Unlock** một cú bấm |
| B6 | Phạm vi | Frontend + backend nhẹ (endpoint mới, nới ràng buộc); **không** migration |

## Giao diện

Route giữ nguyên: `/admin/study-plans/new` tạo plan trống rồi chuyển ngay sang `/admin/study-plans/[slug]`.

```
┌─ Outline ─────────────────┬─ Stage 2 · Inheritance ──────────────────────────┐
│ OOP with Java       DRAFT │ [Statement] [Tests 14] [Grading] [Starter] [Ref] │
│ ▾ Mini Bank               │                                                  │
│   ▸ 1 Account        ✓    │  … panel soạn đề, dùng lại component hiện có …   │
│   ▾ 2 Inheritance    ●    │                                                  │
│       ≡ Inheritance       │ Topics before this stage                         │
│       ≡ Polymorphism  ⚠   │  ≡ Inheritance      PUBLISHED   [Open] [×]       │
│       + topic             │  ≡ Polymorphism     DRAFT       [Open] [×]       │
│   + stage                 │  [+ New topic] [Attach existing…]                │
│ ▸ Practice: Shapes        │                                                  │
│ + project  + practice     │                                                  │
├───────────────────────────┴──────────────────────────────────────────────────┤
│ Saved 3s ago · 2 issues                         [Preview] [Publish…]         │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Outline (cột trái)

- Cây: Plan › Item (Project / Practice) › Stage › Topic. Chọn một nút ⇒ cột phải hiện chi tiết của nút đó. URL giữ lựa chọn: `?node=stage:12`.
- Kéo thả để sắp xếp trong cùng cấp (item trong plan, stage trong project, topic trong stage). Bàn phím: chọn nút, `Alt+↑ / Alt+↓`. Không kéo qua cấp khác.
- Dấu trạng thái mỗi nút: `✓` PUBLISHED, `●` DRAFT, `⚠` có lỗi checklist.
- `+ stage` ⇒ nhân bản stage cuối (B3); project chưa có stage ⇒ hỏi "Đề trống" hoặc "Chọn đề có sẵn".
- `+ topic` ⇒ ô tìm kèm "Tạo topic mới 'xyz'".
- Xoá nút: menu `⋯` › Remove. Plan đã phát hành ⇒ hộp xác nhận ghi số học viên đã nộp bài stage đó.

### Chi tiết (cột phải)

| Nút | Nội dung |
|-----|----------|
| Plan | Tiêu đề, slug (tự sinh, sửa được), mô tả |
| Item | Loại, tiêu đề, mô tả |
| Stage | Các tab soạn đề dùng lại từ `/admin/problems` (`TestCasesPanel`, `GradingPanel`, `ReferencePanel`, `SourceFilesEditor`) + danh sách topic của stage. Có link "Open in full editor" |
| Topic | Nhúng `TopicEditor` hiện có (bỏ header / footer riêng). Theory có tab **Preview** dùng `react-markdown` sẵn có |

### Lưu

- **Autosave**: debounce 1 s sau lần sửa cuối, mỗi thực thể lưu qua API của nó (plan `PUT /study-plans/{slug}`, đề `PUT /problems/{id}` …). Thanh dưới: `Saving… / Saved 3s ago / Save failed — Retry`.
- Rời trang khi còn thay đổi chưa lưu ⇒ `beforeunload` + chặn điều hướng nội bộ.
- Lỗi kiểm tra hiện **ngay tại ô** và gom vào checklist; không chặn autosave bản nháp.
- Hai tab cùng sửa: lần lưu sau thắng (như hiện nay). Ghi nhận, không xử lý ở bản này.

### Publish (B4)

`Publish…` mở hộp checklist (gọi `GET /study-plans/{slug}/publish-check`):

```
Publish "OOP with Java"
  ✓ 3 items, 7 stages
  ✗ Stage 4 "Bank": no public test case                 [Go to]
  ✗ Topic "Polymorphism": quiz step 2 has no correct option [Go to]
  ! Stage 5: reference solution not run since last edit [Run now]
  Will publish: plan, 2 draft problems, 1 draft topic
                                        [Cancel] [Publish]
```

- `✗` chặn publish; `!` chỉ cảnh báo.
- Publish luôn lưu thay đổi đang chờ trước (sửa U2).
- Sau khi publish, nút đổi thành `Published · Unpublish ▾` (Unpublish = về DRAFT, Archive).

### Sửa khi đã phát hành (B5)

- Banner trên cùng: `Live · 23 learners. Changes are visible immediately.`
- Mô tả đề, starter, topic, thứ tự, tiêu đề: autosave như bản nháp.
- Testcase / grading của đề PUBLISHED: tab hiện `Locked while live` + nút **Unlock to edit**. Hộp xác nhận: *"Stage 4 will be unavailable to 5 learners currently on it until you publish again."* ⇒ đề về DRAFT. Thanh dưới hiện `1 stage unlocked · [Publish changes]`; Publish changes đi qua checklist như trên.
- Lần AC cũ vẫn tính (giữ Q11).

## Backend (nhẹ, không migration)

| # | Thay đổi | Ghi chú |
|---|----------|---------|
| BE-1 | Plan **DRAFT** nhận đề DRAFT / REVIEWING; plan PUBLISHED vẫn chỉ nhận đề PUBLISHED | sửa `StudyPlanService.java:157`. Đề DRAFT không lộ ra học viên (đã lọc theo status) |
| BE-2 | `POST /api/v1/admin/problems/{id}/clone` `{slug, title}` ⇒ đề DRAFT mới: mô tả, type, tags, grading config, starter, lời giải mẫu, testcase (giữ `label`, `hidden`, thứ tự) | quyền như sửa đề |
| BE-3 | `POST /api/v1/study-plans/{slug}/items/{itemIndex}/stages` `{mode: "clone" \| "empty", title}` ⇒ tạo đề (clone stage cuối hoặc trống) và gắn vào cuối project trong một transaction | builder dùng cho `+ stage` |
| BE-4 | `GET /api/v1/study-plans/{slug}/publish-check` ⇒ `{errors:[{node, message}], warnings:[…], willPublish:{problems:[id], topics:[id]}}` | lỗi lấy từ luật sẵn có: đề cần testcase công khai, grading hợp lệ (`GradingConfigValidator`), topic step hợp lệ, item PROBLEM đúng 1 stage, plan ≥ 1 item |
| BE-5 | `POST /api/v1/study-plans/{slug}/publish` ⇒ chạy lại check; publish mọi đề DRAFT (DRAFT → REVIEWING → PUBLISHED), topic DRAFT, rồi plan; **một transaction**, lỗi thì không đổi gì | đề / topic của người khác mà người gọi không sửa được ⇒ lỗi trong check |
| BE-6 | `POST /api/v1/admin/problems/{id}/unlock` ⇒ PUBLISHED → ARCHIVED → DRAFT trong một lần | trả số học viên bị ảnh hưởng |
| BE-7 | `PlanDetail` cho staff thêm `learnerCount` (số user có submission chính thức trên đề của plan) và mỗi stage thêm `problemStatus`, `learnersOnStage` | 1 truy vấn đếm, không N+1 |

Cảnh báo "reference solution not run since last edit" cần biết lần chạy cuối. Nếu `ProblemAuthoringService` chưa lưu thời điểm này, bỏ cảnh báo ở bản đầu (không migration).

## Ngoài phạm vi

- Sửa đồng thời nhiều người, lịch sử phiên bản, hoàn tác.
- Dashboard tiến độ học viên (chỉ có số đếm ở BE-7).
- Import / export khoá học bằng file.
- Dùng một đề cho nhiều plan (giữ `UNIQUE(problem_id)`).
- Đổi trang `/admin/problems`, `/admin/topics` (vẫn dùng được độc lập).

## Acceptance Criteria

- **AC-CB-01** Given plan trống · When thêm project, bấm `+ stage` 3 lần (stage 1 đề trống) · Then có 3 đề DRAFT; stage 2, 3 có testcase của stage trước, không rời trang.
- **AC-CB-02** Given sửa tiêu đề stage · When đợi 1 s · Then thanh dưới hiện `Saved`; tải lại trang thấy tiêu đề mới.
- **AC-CB-03** Given còn thay đổi chưa lưu · When bấm link sang trang khác · Then hỏi xác nhận.
- **AC-CB-04** Given một đề không có testcase công khai · When mở Publish · Then checklist có `✗` trỏ tới stage đó, nút Publish bị khoá; bấm `Go to` chọn đúng nút trên outline.
- **AC-CB-05** Given checklist sạch, 2 đề và 1 topic DRAFT · When Publish · Then plan, 2 đề, topic đều PUBLISHED; học viên thấy plan.
- **AC-CB-06** Given lỗi giữa chừng khi publish (vd. đề bị người khác xoá) · Then không thực thể nào đổi trạng thái.
- **AC-CB-07** Given plan live, stage 4 có 5 học viên · When Unlock testcase · Then hộp xác nhận ghi "5 learners", đề về DRAFT, học viên thấy "Stage is being updated".
- **AC-CB-08** Given outline có 3 stage · When kéo stage 3 lên đầu (hoặc `Alt+↑` hai lần) · Then thứ tự mới được lưu; topic đi theo stage.
- **AC-CB-09** Given plan PUBLISHED · When thêm đề DRAFT qua API cũ `PUT /study-plans/{slug}` · Then `409` (giữ luật cho plan live).

## Kế hoạch triển khai

1. **Backend**: BE-1, BE-2, BE-6, BE-7 + integration test (`StudyPlanIntegrationTest`, `ProblemAuthoringIntegrationTest`).
2. **Backend**: BE-3, BE-4, BE-5 + test transaction (AC-CB-05, AC-CB-06).
3. **Frontend khung**: layout 2 cột, outline, chọn nút qua URL, kéo thả + phím, autosave, chặn rời trang. Panel Plan / Item.
4. **Frontend Stage / Topic**: nhúng panel soạn đề và `TopicEditor`; preview Markdown.
5. **Publish + Unlock**: hộp checklist, banner live.
6. **E2E**: viết lại `admin-study-plans.spec.ts` theo AC-CB-01…08; mock (`USE_MOCK`) cho endpoint mới.

## Đã chốt sau review

- **C1.** Unlock đưa đề về DRAFT; học viên đang ở stage đó tạm thấy "Stage is being updated" cho tới khi publish lại. **Chấp nhận** — không làm bản nháp song song.
- **C2.** Plan của người khác vẫn chỉ đọc với INSTRUCTOR. **Không** làm "Duplicate plan".
