> Bài code của chủ đề **Đóng gói (Encapsulation)** (Stage 1 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: ví điện tử
Viết lớp **`Wallet`**:

- Field `private final String owner` và `private long balance` (bắt đầu từ 0). **Không có field `public`, không có setter.**
- Constructor `public Wallet(String owner)`.
- `public void deposit(long amount)`: `amount <= 0` ⇒ `IllegalArgumentException("Số tiền phải dương")`.
- `public void pay(long amount)`: `amount <= 0` ⇒ `IllegalArgumentException("Số tiền phải dương")`; `amount > balance` ⇒ `IllegalArgumentException("Không đủ tiền")`. Lỗi thì số dư giữ nguyên.
- `public long getBalance()`, `public String getOwner()`.

`Main` đọc:

- Dòng 1: tên chủ ví.
- Các dòng sau (tới hết input), mỗi dòng một lệnh:

| Lệnh | Output |
|------|--------|
| `NAP <tiền>` | `OK, số dư: <số dư>` |
| `TRA <tiền>` | `OK, số dư: <số dư>` |
| `XEM` | `<chủ ví>: <số dư>` |
| lỗi ở `NAP` / `TRA` | `Lỗi: <message của exception>` |

**Ví dụ**

Input:

```
Lan
NAP 100000
TRA 30000
TRA 500000
NAP 0
XEM
```

Output:

```
OK, số dư: 100000
OK, số dư: 70000
Lỗi: Không đủ tiền
Lỗi: Số tiền phải dương
Lan: 70000
```

### Cách chấm
- **Rule cấu trúc**: mọi field của `Wallet` không `public`; có `public void deposit(long)`, `public void pay(long)`, `public long getBalance()`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
