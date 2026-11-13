### Project Mini Bank Console — Stage 1/5: Tài khoản & đóng gói

Trong project này bạn xây dần một **ngân hàng mini chạy trên console**. Mỗi stage thêm tính năng mới; **mọi testcase của stage trước vẫn được chấm lại**, nên đừng làm hỏng phần đã chạy.

Ở stage này: mở tài khoản lúc khởi động, nạp tiền, xem số dư.

### Học trước

Các chủ đề ngay phía trên stage này trong plan (lý thuyết, quiz, kéo thả, điền code, bài code nhỏ):

- **Lớp, đối tượng & constructor**: id tự tăng bằng field `static`, ghi đè `toString()`.
- **Đóng gói**: field `private`, `deposit` từ chối số tiền `<= 0` bằng `IllegalArgumentException`.

### Yêu cầu

1. **`Account`**: id tự tăng bắt đầu từ `1`, loại tài khoản, chủ tài khoản, số dư. **Không field nào `public`** (judge kiểm tra bằng reflection).
   - `public long deposit(long amount)`: nạp tiền, trả về số dư mới; `amount <= 0` ⇒ `IllegalArgumentException`.
   - `public long getBalance()`.
   - `toString()` trả về `String.format("#%d %-12s %-10s %,15d", id, loại, chủ, số dư)`, với loại là `SAVINGS` hoặc `CHECKING`.
2. **`Bank`**: giữ danh sách tài khoản; `open(type, owner, initial)` với `type` là `S` (SAVINGS) hoặc `C` (CHECKING); `find(id)`; `totalBalance()`.
3. **`Main`** đọc từ `System.in` bằng `Scanner`:
   - In `=== Mini Bank ===`.
   - In `Số tài khoản cần mở: ` (không xuống dòng), đọc `n`. Với mỗi tài khoản `i = 1..n` in lần lượt `Chủ tài khoản #i: `, `Loại (S = tiết kiệm, C = thanh toán): `, `Số dư ban đầu: ` (đều không xuống dòng) rồi đọc từng giá trị; mở xong in `-> Đã mở ` + tài khoản.
   - In bảng lệnh bằng `printHelp()` có sẵn trong file khung.
   - Lặp: in `> ` (không xuống dòng), đọc một dòng lệnh:

| Lệnh | Output |
|------|--------|
| `DEPOSIT <id> <tiền>` | `Số dư mới: <số dư>` (định dạng `%,d`) |
| `BALANCE <id>` | tài khoản (`toString()`) |
| `LIST` | mỗi tài khoản một dòng, theo thứ tự mở |
| `EXIT` | `Bye!` rồi kết thúc chương trình |

   - Hết input (không còn dòng nào, Ctrl+D): in **một dòng trống** rồi `Kết thúc input. Tổng tài sản: <tổng>` (định dạng `%,d`).

Lỗi (số tiền không hợp lệ, sai id…) in ra **`System.err`** và chương trình chạy tiếp. Judge **chỉ so stdout**, từng dòng: bỏ qua khoảng trắng thừa (căn cột không cần khớp từng dấu cách) và dòng trống, nhưng chữ, số và dấu câu phải đúng. Sai ở testcase công khai, kết quả Check chỉ ra dòng lệch đầu tiên; sai ở testcase ẩn thì cho biết testcase đó thuộc stage nào.

### Ví dụ

Input:

```
2
An
S
100000
Binh
C
0
DEPOSIT 2 50000
BALANCE 1
LIST
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Số tài khoản cần mở: Chủ tài khoản #1: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #1 SAVINGS      An                 100,000
Chủ tài khoản #2: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #2 CHECKING     Binh                     0
Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> Số dư mới: 50,000
> #1 SAVINGS      An                 100,000
> #1 SAVINGS      An                 100,000
#2 CHECKING     Binh                50,000
> Bye!
```

Prompt in bằng `print` (không xuống dòng) nên input bạn gõ không xuất hiện trong stdout — các prompt nối liền nhau như trên.

### Gợi ý
- Sau `nextInt()` / `nextLong()` nhớ gọi `nextLine()` để bỏ ký tự xuống dòng còn lại.
- Đọc chủ tài khoản bằng `nextLine()` (tên có thể có khoảng trắng); loại bằng `next()`.
