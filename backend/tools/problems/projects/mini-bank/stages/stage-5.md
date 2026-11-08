### Project Mini Bank Console — Stage 5/5: Hoàn thiện CLI

Stage cuối: hoàn thiện bảng lệnh và mã thoát. Xong stage này bạn có một chương trình console hoàn chỉnh dùng đủ đóng gói, kế thừa, đa hình và exception.

### Yêu cầu

| Lệnh | Output |
|------|--------|
| `HELP` | in lại bảng lệnh (`printHelp()`) |
| `REPORT <n>` | `n` dòng `Report line <i> - total <tổng>` với `i = 1..n`, `<tổng>` là tổng số dư **không** định dạng dấu phẩy; `n = 0` không in gì |
| `EXIT [mã]` | in `Bye!` rồi thoát với exit code `mã` (mặc định `0`) |

Mọi thứ của stage 1–4 giữ nguyên — toàn bộ testcase của 4 stage trước được chấm lại ở đây.

### Ví dụ

Input:

```
1
Minh
C
10
HELP
REPORT 3
REPORT 0
EXIT
```

Output (stdout):

```
=== Mini Bank ===
Số tài khoản cần mở: Chủ tài khoản #1: Loại (S = tiết kiệm, C = thanh toán): Số dư ban đầu: -> Đã mở #1 CHECKING     Minh                    10
Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>
      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]
> Report line 1 - total 10
Report line 2 - total 10
Report line 3 - total 10
> > Bye!
```

### Hoàn thành project 🎉
Bài nộp `AC` ở stage này hoàn thành project Mini Bank trong lộ trình "OOP với Java".
