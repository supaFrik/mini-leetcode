### Project Mini Bank Console — Stage 2/5: Kế thừa & đa hình

Hai loại tài khoản giờ có **quy tắc khác nhau**. Thay vì `if (loại == ...)` khắp nơi, hãy để mỗi lớp con tự quyết định.

### Yêu cầu

1. **`Account` thành lớp `abstract`** (bỏ field "loại" của stage 1):
   - `protected abstract boolean canWithdraw(long amount)`
   - `public abstract void endOfMonth()`
   - `public abstract String getType()`
   - `public long withdraw(long amount)`: `amount <= 0` ⇒ lỗi; không `canWithdraw(amount)` ⇒ lỗi và **giữ nguyên số dư**; được thì trừ tiền, trả về số dư mới.
2. **`SavingsAccount extends Account`** (`getType()` = `SAVINGS`): sau khi rút số dư phải còn **ít nhất 50.000**; cuối tháng cộng lãi **0,5%**, làm tròn bằng `Math.round(balance * 0.005)`.
3. **`CheckingAccount extends Account`** (`getType()` = `CHECKING`): được thấu chi, số dư sau khi rút **không thấp hơn -500.000**; cuối tháng **trừ phí 10.000** (có thể xuống dưới hạn mức thấu chi).
4. Lệnh mới:

| Lệnh | Output |
|------|--------|
| `WITHDRAW <id> <tiền>` | thành công: `Số dư mới: <số dư>`; thất bại: không in gì ra stdout (lỗi ra `System.err`) |
| `MONTH` | gọi `endOfMonth()` cho **mọi** tài khoản, in `Đã tính lãi / phí cuối tháng.` |

Mọi thứ của stage 1 giữ nguyên (testcase stage 1 vẫn được chấm).

### Ví dụ

Input:

```
1
Chi
S
80000
WITHDRAW 1 30001
WITHDRAW 1 30000
WITHDRAW 1 1
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Số tài khoản cần mở: Chủ tài khoản #1: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #1 SAVINGS      Chi                 80,000
Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> > Số dư mới: 50,000
> > #1 SAVINGS      Chi                 50,000
> Bye!
```

Lệnh rút đầu và cuối thất bại (còn dưới 50.000) nên chỉ thấy prompt `> `.
