> Bài code của chủ đề **Interface & tách lệnh (Command)** (Stage 5 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: bộ xử lý lệnh
Viết:

- **`interface Command`**: `String name()` và `String run(String args)`.
- **`EchoCommand`** (`ECHO`): trả lại `args` (khoảng trắng thừa gộp thành một).
- **`AddCommand`** (`ADD`): cộng các số nguyên trong `args` (không có số nào ⇒ `0`); token không phải số ⇒ ném `IllegalArgumentException("Không phải số: <token>")`.
- **`UpperCommand`** (`UPPER`): trả `args` viết hoa (`Locale.ROOT`).

Cả ba `implements Command`. `Main` đăng ký chúng vào `Map<String, Command>` rồi đọc tới hết input. Mỗi dòng: `<TÊN> <args...>`:

- `HELP`: in `Lệnh: <tên các lệnh đã đăng ký, sắp xếp, cách nhau bởi ", ">`.
- Tên đã đăng ký: in kết quả `run(args)`; nếu ném `IllegalArgumentException` thì in `Lỗi: <message>`.
- Tên lạ: in `Lệnh không hỗ trợ: <TÊN>`.

**Ví dụ**

Input:

```
HELP
ECHO xin   chào
ADD 1 2 3
ADD 4 x
UPPER mini bank
PING
```

Output:

```
Lệnh: ADD, ECHO, UPPER
xin chào
6
Lỗi: Không phải số: x
MINI BANK
Lệnh không hỗ trợ: PING
```

### Cách chấm
- **Rule cấu trúc**: `Command` là interface có `name()` và `run(String)`; `EchoCommand`, `AddCommand`, `UpperCommand` đều `implements Command`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
