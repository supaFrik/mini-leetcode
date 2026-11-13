### Project Mini Bank Console — Stage 4/5: Lớp Bank & mở tài khoản

`Main` nên chỉ đọc lệnh và in kết quả; mọi quy tắc nghiệp vụ nằm trong `Bank` và các lớp tài khoản.

### Học trước

Các chủ đề ngay phía trên stage này trong plan (lý thuyết, quiz, kéo thả, điền code, bài code nhỏ):

- **Collections & đóng gói danh sách**: `Bank` giữ tài khoản trong collection `private`, tìm theo id bằng `Map`, không trả danh sách bên trong ra ngoài.

### Yêu cầu

1. **`Bank` đóng gói** danh sách tài khoản (field `private`, không để `Main` sửa trực tiếp) và có:
   - `public Account open(String type, String owner, long initialBalance)` — `type` không phải `S` / `C` (không phân biệt hoa thường) ⇒ `IllegalArgumentException`, **không tạo tài khoản và không tốn id**.
   - `public Account find(int id)` — không có ⇒ `IllegalArgumentException`.
   - `public void transfer(int fromId, int toId, long amount) throws InsufficientFundsException` — chuyển logic `TRANSFER` của stage 3 vào đây.
2. Lệnh mới `OPEN <S|C> <số dư> <tên>`: tên có thể gồm nhiều từ (phần còn lại của dòng). Thành công in `-> Đã mở ` + tài khoản; loại sai ⇒ lỗi ra `System.err`.
3. Loại sai lúc khởi động (bước hỏi `Loại`) cũng xử lý như vậy: báo lỗi và bỏ qua tài khoản đó.

Mọi thứ của stage 1–3 giữ nguyên.

### Ví dụ

Input:

```
0
OPEN S 70000 Le Thi Hong Nhung
OPEN C 5000 Pham Quang
LIST
TRANSFER 1 2 15000
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Số tài khoản cần mở: Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> -> Đã mở #1 SAVINGS      Le Thi Hong Nhung          70,000
> -> Đã mở #2 CHECKING     Pham Quang           5,000
> #1 SAVINGS      Le Thi Hong Nhung          70,000
#2 CHECKING     Pham Quang           5,000
> Đã chuyển.
> #1 SAVINGS      Le Thi Hong Nhung          55,000
#2 CHECKING     Pham Quang          20,000
> Bye!
```

`%-10s` không cắt tên dài hơn 10 ký tự — tên dài đẩy cột số dư sang phải như trên.
