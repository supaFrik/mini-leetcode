## Mục tiêu
- Nắm cây phân cấp `Throwable` và khác biệt **checked / unchecked**.
- Dùng `throw`, `throws`, `try / catch / finally` đúng chỗ.
- Tự định nghĩa exception mang ý nghĩa nghiệp vụ.
- Bọc exception kỹ thuật thành exception nghiệp vụ mà không mất nguyên nhân gốc.

## 1. Cây phân cấp

```
Throwable
├── Error                 lỗi của JVM (OutOfMemoryError...) — đừng bắt
└── Exception             CHECKED: trình biên dịch bắt bạn xử lý
    ├── IOException, ... (và exception bạn tự viết extends Exception)
    └── RuntimeException  UNCHECKED: không bắt buộc xử lý
        ├── IllegalArgumentException
        ├── NumberFormatException
        ├── NullPointerException
        └── ArrayIndexOutOfBoundsException
```

- **Checked** (`extends Exception`): tình huống **có thể xảy ra dù code đúng** — file không tồn tại, tài khoản không đủ tiền. Trình biên dịch buộc nơi gọi phải `catch` hoặc khai báo `throws` tiếp.
- **Unchecked** (`extends RuntimeException`): thường là **lỗi lập trình** hoặc đầu vào vi phạm hợp đồng — tham số âm, `null`, chỉ số vượt mảng.

## 2. `throw` và `throws`

```java
public long withdraw(long amount) throws InsufficientFundsException {   // khai báo: có thể ném
    if (amount > balance) {
        throw new InsufficientFundsException("Không đủ tiền");         // ném thật
    }
    balance -= amount;
    return balance;
}
```

- `throw` là **câu lệnh**: dừng method ngay, đẩy exception lên nơi gọi.
- `throws` là **khai báo** trong chữ ký: báo cho nơi gọi biết phải xử lý.

## 3. `try / catch / finally`

```java
try {
    account.withdraw(amount);
    System.out.println("Thành công");
} catch (InsufficientFundsException e) {
    System.err.println(e.getMessage());
} catch (NumberFormatException | IllegalArgumentException e) {   // multi-catch
    System.err.println("Dữ liệu sai: " + e.getMessage());
} finally {
    System.out.println("Luôn chạy, kể cả khi có lỗi");          // dọn dẹp tài nguyên
}
```

- `catch` khớp theo **thứ tự từ trên xuống**: đặt lớp con trước lớp cha.
- Không bao giờ "nuốt" lỗi bằng `catch (Exception e) {}` rỗng — lỗi biến mất không dấu vết.
- Với tài nguyên cần đóng (file, socket), dùng `try (var r = ...) { }` (try-with-resources).

## 4. Exception tự định nghĩa

```java
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Tên exception là một phần của API: nơi gọi đọc `throws InsufficientFundsException` là hiểu ngay chuyện gì có thể xảy ra, thay vì một `Exception` chung chung.

## 5. Bọc exception (exception chaining)

Đầu vào sai định dạng gây `NumberFormatException` — một lỗi **kỹ thuật**. Ở tầng nghiệp vụ, bạn muốn báo lỗi **nghiệp vụ** nhưng vẫn giữ nguyên nhân gốc để debug:

```java
try {
    age = Integer.parseInt(raw);
} catch (NumberFormatException e) {
    throw new InvalidAgeException("Không phải số: " + raw, e);   // e thành getCause()
}
```

## 6. Chương trình không được "chết" vì input sai

Với chương trình đọc lệnh, mỗi lệnh nên được bọc trong `try / catch` riêng: lệnh lỗi thì báo và **chạy tiếp lệnh sau**, không để một dòng sai làm dừng cả chương trình.

## Lỗi thường gặp
| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| `catch (Exception e) {}` rỗng | Lỗi bị giấu, dữ liệu sai âm thầm | Ít nhất ghi lại `e.getMessage()` |
| Đặt `catch (Exception)` trước `catch` cụ thể | Lỗi biên dịch "already been caught" | Lớp con trước, lớp cha sau |
| Dùng exception để điều khiển luồng bình thường | Code khó đọc, chậm | Kiểm tra điều kiện bằng `if` khi có thể |
| Bọc lỗi mà không truyền `cause` | Mất stack trace gốc | `new XxxException(msg, e)` |

## Trong Mini Bank
Stage 3 yêu cầu `InsufficientFundsException extends Exception` (checked) và `withdraw` khai báo `throws`. Lệnh `TRANSFER` rút ở tài khoản này rồi nạp vào tài khoản kia. Mọi input sai (số không hợp lệ, sai id, thiếu tham số) phải in ra `System.err` và chương trình chạy tiếp.

## Tóm tắt
- Checked = tình huống nghiệp vụ có thể xảy ra, phải xử lý; unchecked = lỗi lập trình / vi phạm hợp đồng.
- `throw` ném, `throws` khai báo; `catch` từ cụ thể tới chung; `finally` luôn chạy.
- Exception tự định nghĩa làm API rõ nghĩa; bọc lỗi kỹ thuật thì giữ `cause`.
- Chương trình đọc lệnh: mỗi lệnh một `try / catch`, lỗi thì báo rồi chạy tiếp.
