# Frontend Mini LeetCode (Kỹ sư 3), Giai đoạn 1 đến 3 (bản 2)

## 1. Áp dụng (PowerShell, đứng ở thư mục gốc `mini-leetcode`)

```powershell
# 1) Giải nén ĐÈ vào frontend/
Expand-Archive -Path .\frontend-update.zip -DestinationPath .\frontend -Force

# 2) XOÁ file cũ. Bắt buộc: src/app/page.tsx sẽ trùng route "/" với src/app/(main)/page.tsx và làm build lỗi
Remove-Item .\frontend\src\app\page.tsx -ErrorAction SilentlyContinue
Remove-Item .\frontend\src\lib\problems.ts -ErrorAction SilentlyContinue

# 3) Cài thư viện (font tự host thay cho Google Fonts, và khoá phiên bản chia panel)
cd frontend
npm i @fontsource-variable/inter @fontsource-variable/jetbrains-mono react-resizable-panels@^2.1.9

# 4) .env.local cần đủ 4 biến, xem .env.example

npx tsc --noEmit      # không in gì là đạt
npm run build         # phải thành công, không cần Internet
npm run dev
```

Zip ghi đè: cấu hình gốc, `src/app/*`, `src/components/*`, `src/hooks/*`, `src/lib/*` (trừ `utils.ts`), `src/types/api.ts`
và đúng MỘT file shadcn là `src/components/ui/resizable.tsx`.

## 2. Chạy thử không cần backend (NEXT_PUBLIC_USE_MOCK=true)

- Trang đăng nhập có 3 nút vào nhanh: Học viên, Giảng viên, Admin.
- Kỳ thi mẫu (id 1, 2, 3): kỳ 1 đang diễn ra (còn 45 phút, đã đăng ký), kỳ 2 sắp diễn ra (chưa đăng ký), kỳ 3 đã kết thúc.
- Thử anti-cheat: đăng nhập, vào `/exam/1/problems/1`. Trang kỳ thi có link "đặt lại kỳ thi này".
- Judge giả: giữ nguyên template là WA, có `while(true)` là TLE, có `throw new` là RE, có `syntax error` là CE, còn lại là AC.

## 3. Các trang

| URL | Nội dung |
|---|---|
| `/` | Trang chủ: hero, tiến độ cá nhân, bài gợi ý, kỳ thi đang diễn ra, bài nộp gần đây |
| `/login` | Đăng nhập / đăng ký, lỗi theo từng ô (RFC 7807), quay lại trang đang vào (`?next=`, chặn open-redirect) |
| `/problems` | Danh sách: lọc độ khó / tag / tìm kiếm, chip bộ lọc, phím `/`, ngôn ngữ làm bài mặc định |
| `/problems/two-sum` | Workspace: Monaco, kết quả chấm, diff, tab Submissions, toast lỗi, bố cục mobile |
| `/exam` | Kỳ thi theo nhóm (Đang diễn ra / Sắp diễn ra / Đã kết thúc), đếm ngược |
| `/exam/1` | Tổng quan kỳ thi, đăng ký, báo cáo giám sát và đạo văn (giảng viên/admin) |
| `/exam/1/problems/1` | Phòng thi có giám sát |
| `/exam/1/leaderboard` | Bảng xếp hạng ACM/ICPC |
| `/submissions`, `/submissions/[id]` | Lịch sử nộp bài, xem lại code, diff testcase |

## 4. Chống gian lận: những gì xảy ra ở `/exam/1/problems/1`

- Cổng vào thi: đọc quy chế, tick đồng ý, kiểm tra mạng, rồi bấm Bắt đầu (cú click này xin fullscreen).
- Rời tab, mất focus cửa sổ, hoặc **thoát toàn màn hình bằng Esc**: client chỉ báo `WINDOW_BLUR` / `WINDOW_FOCUS`, server tự tính ΔT. Dưới 1,5s không phạt.
- Dán code (Ctrl+V, Shift+Insert, menu, kéo thả): chặn và báo `CLIPBOARD_PASTE`, server phạt ngay. Chống đếm trùng 500ms.
- Có vi phạm: modal bắt buộc xác nhận (không đóng bằng Esc hay bấm ra ngoài). Nút "Vi phạm n/3" mở nhật ký giám sát kèm thông điệp từ server.
- Đủ 3 lần: lớp phủ "Bài thi đã bị khoá", editor chỉ đọc, nút Submit bị disabled, bản nháp cuối được đẩy lên server.
- Hết giờ (theo giờ server): khoá nộp bài, lưu nháp cuối. Mất mạng: banner vàng, bài vẫn lưu cục bộ, có mạng lại tự đồng bộ.
- Có bộ chuyển đề A/B/C trong thanh công cụ. F5 sẽ phải qua cổng vào thi lại.

## 5. Endpoint backend chưa có trong spec (đánh dấu [ĐỀ XUẤT] trong `services.ts`)

| Endpoint đề xuất | Dùng cho | Hỏi |
|---|---|---|
| `GET /api/v1/contests`, `GET /api/v1/contests/{id}` (endTime, status, problems, `myStatus`, `myViolations`; `myStatus` rỗng nghĩa là chưa đăng ký) | Tab Exam, đồng hồ, cổng vào thi | KS1 |
| `POST /api/v1/contests/{id}/register` | Nút Đăng ký tham gia | KS1 |
| `GET /api/v1/contests/{id}/leaderboard` | Bảng xếp hạng | KS1 |
| `GET /api/v1/contests/{id}/proctoring/report` (INSTRUCTOR, ADMIN) | Báo cáo vi phạm | KS1 |
| `POST /api/v1/contests/{id}/participants/{userId}/unlock` (ADMIN) | Mở khoá thí sinh | KS1 |
| `GET /api/v1/contests/{id}/plagiarism` | Danh sách "Cần phúc khảo" (Jaccard, ngưỡng 80%) | KS2 |
| `GET /api/v1/submissions/{id}/results` | Chip Case, Diff | KS1 + KS2 |
| `GET/PUT /api/v1/contests/{cid}/problems/{pid}/draft?language=` | Đồng bộ nháp 15s | KS1 |
| `POST /api/v1/problems/{id}/run` | Nút Run | KS1 + KS2 |
| `GET /problems`: thêm `tags`, `progress` và lọc theo chúng; `GET /problems/{id}/test-cases` trả nội dung | Bộ lọc, ví dụ | KS1 |

Khi backend có endpoint: đặt `NEXT_PUBLIC_USE_MOCK=false`, bật `NEXT_PUBLIC_ENABLE_RUN` / `NEXT_PUBLIC_ENABLE_DRAFT_SYNC` tương ứng.
Đối chiếu Swagger (http://localhost:8080/swagger-ui.html): body login/register, field token, tên field multipart khi thêm testcase,
hình dạng `GET /problems` và `PUT /problems/{id}`. Spec cũng chưa có heartbeat (`{contestId, timestamp, windowFocused}`) nên chưa làm.

## 6. Lưu ý

- Font tự host qua `@fontsource-variable/*` nên `npm run build` không cần Internet.
- Monaco tải từ CDN jsdelivr. Nếu mạng chặn, app tự chuyển sang `<textarea>` dự phòng (vẫn chặn paste ở Exam). Phòng thi thật nên tự host Monaco.
- Chặn paste, chuột phải, fullscreen chỉ là UX. Server mới là bên phạt (Server-Side Authority).
- Nếu `tsc` báo thiếu icon (vd `Shield`, `Trophy`), thư viện lucide của bạn cũ hơn bản đã kiểm thử (1.x): đổi sang tên icon tương ứng.
- Đã kiểm thử tự động (Chromium + mock) 68 kịch bản và `next build` thành công. CHƯA kiểm thử với backend thật, CHƯA kiểm thử Safari/Firefox, và Monaco chưa được chạy thật trong môi trường kiểm thử (CDN bị chặn nên đã kiểm bằng editor dự phòng).
