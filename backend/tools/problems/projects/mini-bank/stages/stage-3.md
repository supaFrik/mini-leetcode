### Project Mini Bank Console — Stage 3/5: Exception & chuyển khoản

Rút tiền thất bại không còn là "trả về false": nó là một **tình huống ngoại lệ có kiểm tra** mà người gọi bắt buộc phải xử lý.

### Học trước

Bài trong nhóm *Kiến thức cho Stage 3* của plan:

- **Bài 5 — Exception**: `InsufficientFundsException` là checked exception, `withdraw` khai báo `throws`; mỗi lệnh một `try / catch` để input sai không làm dừng chương trình.

### Yêu cầu

1. **`InsufficientFundsException extends Exception`** (checked exception).
2. `Account.withdraw(long)` khai báo `throws InsufficientFundsException` và ném nó khi `!canWithdraw(amount)`.
3. Lệnh mới `TRANSFER <từ> <đến> <tiền>`:
   - Thành công: in `Đã chuyển.`
   - **Nguyên tử**: sai id (tài khoản nguồn *hoặc* đích không tồn tại) hay không đủ tiền ⇒ **không tài khoản nào thay đổi**. Gợi ý: tìm đủ cả hai tài khoản trước, rồi mới rút và nạp.
4. **Chương trình không bao giờ dừng vì input sai**. Với mọi lệnh, các lỗi sau in ra `System.err` rồi chạy tiếp (không in gì ra stdout):
   - thiếu tham số (`WITHDRAW 1`), tham số không phải số (`BALANCE x`), số tiền `<= 0`, id không tồn tại;
   - lệnh không có trong bảng (`FOO`).

Mọi thứ của stage 1–2 giữ nguyên.

### Ví dụ

Input:

```
2
Dung
S
60000
Em
C
0
TRANSFER 1 2 10000
TRANSFER 2 1 400000
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Số tài khoản cần mở: Chủ tài khoản #1: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #1 SAVINGS      Dung                60,000
Chủ tài khoản #2: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #2 CHECKING     Em                       0
Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> Đã chuyển.
> Đã chuyển.
> #1 SAVINGS      Dung               450,000
#2 CHECKING     Em                -390,000
> Bye!
```
