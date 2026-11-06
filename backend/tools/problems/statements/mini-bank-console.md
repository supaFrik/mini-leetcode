### Đề bài

Xây dựng một **ngân hàng mini chạy trên console**. Chương trình đọc lệnh từ bàn phím bằng `Scanner` và quản lý nhiều loại tài khoản bằng kế thừa + đa hình.

### Yêu cầu

1. **`abstract class Account`**: `id` (tự tăng), `owner`, `balance`; `deposit(long)`, `withdraw(long) throws InsufficientFundsException`; các phương thức trừu tượng `canWithdraw(long)`, `endOfMonth()`, `getType()`.
2. **`SavingsAccount`**: số dư sau khi rút phải còn ít nhất **50.000**; cuối tháng cộng lãi **0,5%** (làm tròn).
3. **`CheckingAccount`**: được thấu chi tới **-500.000**; cuối tháng trừ phí **10.000**.
4. **`InsufficientFundsException`** (checked): ném ra khi rút / chuyển quá hạn mức.
5. **`Bank`**: `open(type, owner, initial)` với `type` là `S` hoặc `C`, `find(id)`, `transfer(from, to, amount)`, `endOfMonth()`, `totalBalance()`.
6. **`Main`**: hỏi số tài khoản cần mở, rồi với mỗi tài khoản hỏi *chủ tài khoản*, *loại*, *số dư ban đầu*; sau đó lặp đọc lệnh tới khi `EXIT` hoặc hết input (Ctrl+D).

| Lệnh | Ý nghĩa |
|------|---------|
| `OPEN <S\|C> <số dư> <tên>` | Mở thêm tài khoản (tên có thể nhiều từ) |
| `DEPOSIT <id> <tiền>` / `WITHDRAW <id> <tiền>` | Nạp / rút |
| `TRANSFER <từ> <đến> <tiền>` | Chuyển khoản |
| `BALANCE <id>` / `LIST` | Xem số dư |
| `MONTH` | Tính lãi / phí cuối tháng cho mọi tài khoản |
| `REPORT <n>` | In `n` dòng báo cáo |
| `EXIT [mã]` | Thoát với exit code `mã` (mặc định 0) |

Lỗi nghiệp vụ (không đủ tiền, sai id, sai lệnh, thiếu tham số) in ra **stderr** và chương trình chạy tiếp.

### Ví dụ

```
=== Mini Bank ===
Số tài khoản cần mở: 2
Chủ tài khoản #1: Nguyễn Văn A
Loại (S = tiết kiệm, C = thanh toán): S
Số dư ban đầu: 100000
-> Đã mở #1 SAVINGS      Nguyễn Văn A         100,000
Chủ tài khoản #2: Trần B
Loại (S = tiết kiệm, C = thanh toán): C
Số dư ban đầu: 0
-> Đã mở #2 CHECKING     Trần B                 0
> WITHDRAW 1 60000
Lỗi: Tài khoản #1 (SAVINGS) không đủ để rút 60,000 — số dư hiện tại 100,000
> TRANSFER 1 2 30000
Đã chuyển.
> MONTH
Đã tính lãi / phí cuối tháng.
> LIST
#1 SAVINGS      Nguyễn Văn A          70,350
#2 CHECKING     Trần B               20,000
```

### Cách chấm

- **40% — cấu trúc OOP**: `Account` là lớp abstract (không field `public`, `canWithdraw` / `endOfMonth` / `getType` abstract), hai lớp con kế thừa và override, `InsufficientFundsException` là checked exception, `Bank` đóng gói (field `private`).
- **60% — testcase stdin/stdout ẩn**: chạy `Main` với input và so **stdout từng dòng** (kể cả các prompt như `Số tài khoản cần mở: `). Dòng lỗi in ra **stderr** không được so.
- Chỉ đạt **AC** khi được 100 điểm.

### Kịch bản test console

Bấm **Run** rồi thử lần lượt:

| # | Case | Cách làm | Kết quả mong đợi |
|---|------|----------|------------------|
| 1 | Prompt không xuống dòng | Chỉ bấm Run | `Số tài khoản cần mở: ` hiện ngay, con trỏ đứng sau dấu `:` |
| 2 | `nextInt()` rồi `nextLine()` | Nhập `1`, rồi tên `Nguyễn Văn A` | Tên đọc đủ cả dòng, không bị bỏ qua |
| 3 | Unicode | Tên có dấu tiếng Việt | Hiện đúng dấu trong `-> Đã mở ...` |
| 4 | Nhiều token / dòng | `DEPOSIT 1 5000` | `Số dư mới: ...` |
| 5 | stderr | `WITHDRAW 1 99999999`, `FOO`, `DEPOSIT 1` | Dòng lỗi màu đỏ, chương trình chạy tiếp |
| 6 | Exception không bắt | Ở câu đầu tiên nhập `abc` | Stack trace `InputMismatchException`, exit code `1` |
| 7 | Exit code tuỳ chọn | `EXIT 3` | `Bye!`, `Process finished with exit code 3` |
| 8 | EOF | Bấm **Ctrl+D** ở dấu `>` | `Kết thúc input. Tổng tài sản: ...`, exit code `0` |
| 9 | Dừng tay | Bấm **Ctrl+C** | `Process stopped` |
| 10 | Không hoạt động | Không gõ gì 30 giây | `Process killed: no input or output for too long` |
| 11 | Giới hạn output | `REPORT 100000` | `Process killed: output limit exceeded` |
| 12 | Chạy lại | Bấm Run khi chương trình đang chạy | Terminal xoá, chương trình chạy lại từ đầu |
| 13 | Lỗi biên dịch | Xoá một dấu `;` trong `Bank.java`, bấm Run | `Bank.java:<dòng>: error: ';' expected`, `Compilation failed` |
| 14 | Type-ahead | Gõ `2` thật nhanh ngay sau khi bấm Run | Input được giữ và đưa vào chương trình khi nó khởi động |
