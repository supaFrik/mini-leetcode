# Chủ đề học (Topics) — lý thuyết, quiz, kéo thả gắn vào stage project

> Mở rộng `specs/study-plan.spec.md`. Tham khảo cách Hyperskill tổ chức project: mỗi stage có một dòng thời gian các chủ đề (Continue / Skip); chủ đề gồm phần lý thuyết có mục lục rồi tới các bài luyện tập nhiều dạng.

## Quyết định đã chốt

| # | Câu hỏi | Chốt |
|---|---------|------|
| T1 | Chủ đề có khoá stage không? | **Không.** Tuỳ chọn, có nút Skip. Stage vẫn chỉ khoá theo stage trước (AC) |
| T2 | Dạng bài phiên bản đầu | Lý thuyết theo section, quiz trắc nghiệm (một / nhiều đáp án), ghép cặp, sắp xếp, điền chỗ trống trong code, bài code (đề có sẵn) |
| T3 | Bài code trong chủ đề | Dùng lại đề có sẵn (7 bài Mini Bank); step CODE trỏ tới đề, xong khi đề có AC |
| T4 | Soạn nội dung | Trình soạn trên web cho INSTRUCTOR / ADMIN (`/admin/topics`); repo có script seed nội dung mẫu |

## Model

- `topics`: `slug` (duy nhất), `title`, `summary`, `est_minutes`, `status` (DRAFT / PUBLISHED / ARCHIVED), `created_by`.
- `topic_steps`: thuộc một topic, có thứ tự. `type` ∈ `THEORY, CHOICE, MATCHING, ORDERING, FILL_BLANK, CODE`; `title`; `content` (JSON công khai); `answer` (JSON **bí mật**, không bao giờ trả cho học viên); `problem_id` (chỉ CODE).
- `study_plan_stage_topics`: (`item_id`, `sort_order`) → (`stage_no`, `topic_id`). Chủ đề gắn vào stage của mục, nằm trong body `PUT /study-plans/{slug}` (thay cả plan như hiện nay). Một chủ đề có thể dùng ở nhiều plan.
- `topic_step_progress`: (`user_id`, `step_id`) → `passed`, `attempts`. `topic_skips`: (`user_id`, `topic_id`).

### Nội dung từng loại step

| Loại | `content` (công khai) | `answer` (bí mật) | Học viên gửi |
|------|----------------------|-------------------|--------------|
| THEORY | `{markdown}` — section tách theo `## ` | — | đánh dấu đã đọc |
| CHOICE | `{prompt, options:[{id,text}], multiple, hint?}` | `{correct:[id], explanation?}` | `{selected:[id]}` |
| MATCHING | `{prompt, left:[{id,text}], right:[{id,text}], hint?}` | `{pairs:{leftId:rightId}, explanation?}` | `{pairs:{leftId:rightId}}` |
| ORDERING | `{prompt, items:[{id,text}], hint?}` | `{order:[id], explanation?}` | `{order:[id]}` |
| FILL_BLANK | `{prompt, code, hint?}` — ô trống viết `[[id]]` trong `code` | `{blanks:{id:[đáp án chấp nhận]}, caseSensitive, explanation?}` | `{values:{id:text}}` |
| CODE | `{problemSlug, intro?}` (server tự điền từ `problem_id`) | — | không gửi; xong khi có AC |

Thứ tự hiển thị của `right` (MATCHING) và `items` (ORDERING) do người soạn nhập; server **xáo trộn ổn định** theo `step_id` trước khi trả để thứ tự không lộ đáp án.

## Chấm và phản hồi

- Chấm ở server, so khớp chính xác tập / ánh xạ / thứ tự. FILL_BLANK: bỏ khoảng trắng đầu cuối, gộp khoảng trắng; không phân biệt hoa thường trừ khi `caseSensitive`.
- Sai: trả `correct=false` và gợi ý mức độ (`2/4 cặp đúng`, `ô 2 chưa đúng`) — không lộ đáp án. Đúng: trả `explanation`.
- Được làm lại; `attempts` đếm số lần gửi.
- **Khoá sau 3 lần sai liên tiếp** (cùng một step luyện tập, đúng một lần thì đếm lại): khoá luyện tập của cả chủ đề 5 phút và đánh dấu mọi step THEORY là chưa đọc (bảng `topic_locks`, Flyway V13). Luyện tập mở lại khi **đã hết 5 phút và đã đọc lại hết lý thuyết** (đọc trong lúc chờ vẫn tính). Trong lúc khoá, gửi bài luyện tập trả `409 TOPIC_LOCKED`. `GET /topics/{slug}` và kết quả attempt trả `lockedUntil`, `mustReread`; attempt trả thêm `wrongStreak`.
- Giao diện: sai lần 2 báo "One more wrong answer locks practice for 5 minutes."; khi khoá hiện thẻ "Practice locked" có đồng hồ đếm ngược và nút "Read the theory". Nút "Go to practice" không tự đánh dấu đã đọc khi đang phải đọc lại.

## Trạng thái chủ đề với người học

`DONE` khi mọi step đã xong (THEORY đã đọc, CODE đã AC); `SKIPPED` khi đã bấm Skip và chưa DONE; `IN_PROGRESS` khi có step đã làm; còn lại `NEW`. Skip đảo ngược được (`DELETE .../skip`).

## API

| Method | Đường dẫn | Ai | Ghi chú |
|--------|-----------|----|---------|
| GET | `/api/v1/topics/{slug}` | đăng nhập | Học viên chỉ thấy PUBLISHED; không có `answer` |
| POST | `/api/v1/topics/{slug}/steps/{stepId}/attempts` | đăng nhập | `{answer}` → `{correct, message, explanation?, topicStatus}` |
| POST / DELETE | `/api/v1/topics/{slug}/skip` | đăng nhập | |
| GET | `/api/v1/topics?search=` | INSTRUCTOR / ADMIN | danh sách để soạn và gắn vào stage |
| GET | `/api/v1/topics/{slug}/authoring` | INSTRUCTOR / ADMIN | kèm `answer` |
| POST, PUT `/{slug}`, DELETE `/{slug}`, POST `/{slug}/transitions` | `/api/v1/topics` | tác giả hoặc ADMIN | giống quyền của study plan |

`GET /study-plans/{slug}`: mỗi `Stage` thêm `topics: [{slug, title, summary, estMinutes, status, stepCount, doneSteps}]` (chỉ chủ đề PUBLISHED với học viên). `UpsertPlanRequest.items[].stageTopics`: `[[topicId...] mỗi stage]`, không bắt buộc.

## Giao diện

- `/study-plans/[slug]`: mục PROJECT hiển thị từng stage có thể mở rộng; trong stage là dòng thời gian các chủ đề (vòng tròn trạng thái, tóm tắt, thời lượng; chủ đề kế tiếp có **Continue** + **Skip**) rồi tới chính stage.
- `/topics/[slug]`: thanh tiến độ theo step; nút **Theory / Practice**, **Skip topic**. Theory: mục lục bên trái, section mở dần bằng **Read more** hoặc **Expand all**. Practice: một thẻ cho mỗi step, **See hint**, **Submit**, phản hồi, **Continue**. Ghép cặp và sắp xếp: kéo thả + nút ↑↓ (dùng được bằng bàn phím).
- `/admin/topics`: danh sách + trình soạn (thông tin chung, thêm step theo loại, sửa, ↑↓, xoá, Publish). `/admin/study-plans`: mục PROJECT có ô chọn chủ đề cho từng stage.

## Tiến độ triển khai (nhánh `feature/topics`)

- [x] Backend: Flyway V12, `StepGrader` (kiểm tra / chấm / nội dung công khai), `TopicService`, `TopicController`, chủ đề trong DTO + upsert của study plan. Test: `StepGraderTest` (9), `TopicIntegrationTest` (4), study plan không đổi (12)
- [x] Web học viên: dòng thời gian stage, `/topics/[slug]` (lý thuyết + 5 widget luyện tập), mock mode; e2e `topics.spec.ts` (3)
- [x] Web soạn: `/admin/topics`, trình soạn mọi loại step, chọn chủ đề cho từng stage; e2e `admin-topics.spec.ts` (3)
- [x] Nội dung: 7 chủ đề Mini Bank (49 step) gắn vào 5 stage, seed bằng `mini_bank_project.py sql`; `MiniBankProjectContentTest` kiểm tra mọi step bằng `StepGrader`
- [x] Chạy thật trên stack Docker: lý thuyết, quiz sai → gợi ý, đúng → giải thích, kéo thả ghép cặp, step code tự xong khi đề đã AC

## Ngoài phạm vi (để sau)

Gems / điểm thưởng, bình luận, "Get unstuck", plugin IntelliJ hiển thị chủ đề (plugin bỏ qua trường mới), lặp lại ngắt quãng.
