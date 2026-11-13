> Kiến thức cho **Stage 1** của project Mini Bank. Đọc phần lý thuyết, rồi làm bài tập ở cuối trang.

### Mục tiêu

- Hiểu vì sao phải **giấu trạng thái** và chỉ cho đổi qua method.
- Dùng đúng các **access modifier**: `private`, (mặc định), `protected`, `public`.
- Viết method **kiểm tra đầu vào** và báo lỗi bằng `IllegalArgumentException`.
- Biết khi nào **không nên** viết setter.

### Lý thuyết

#### 1. Vấn đề: ai cũng sửa được số dư

```java
public class Account {
    public long balance;   // ai cũng gán được
}

account.balance = -1_000_000;   // hợp lệ về cú pháp, sai về nghiệp vụ
```

Số dư âm, số dư bị "bơm" từ bên ngoài, không còn ai đảm bảo được quy tắc nào. Đóng gói giải quyết bằng một nguyên tắc: **dữ liệu là `private`, thay đổi phải đi qua method của chính lớp đó**. Method là "cửa" duy nhất, nên mọi quy tắc nghiệp vụ được kiểm tra ở một chỗ.

#### 2. Access modifier

| Modifier | Ai truy cập được |
|----------|------------------|
| `private` | Chỉ code trong cùng lớp |
| (không ghi) | Các lớp cùng package |
| `protected` | Cùng package + lớp con |
| `public` | Mọi nơi |

Quy tắc ngón tay cái: **field luôn `private`**; method mà lớp khác cần gọi thì `public`; method phụ chỉ dùng nội bộ thì `private`.

#### 3. Bất biến (invariant) và method có kiểm tra

Bất biến là điều **luôn đúng** với mọi đối tượng hợp lệ, ví dụ "số dư không bao giờ âm". Constructor thiết lập nó, mỗi method giữ nó:

```java
public class Account {
    private long balance;

    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền phải dương");
        }
        balance += amount;
    }

    public void withdraw(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Số tiền phải dương");
        if (amount > balance) throw new IllegalArgumentException("Không đủ tiền");
        balance -= amount;
    }

    public long getBalance() {
        return balance;
    }
}
```

Khi đầu vào sai, method **ném exception và không đổi gì**. Nơi gọi bắt lỗi và quyết định làm gì (in thông báo, bỏ qua lệnh...):

```java
try {
    account.withdraw(500);
} catch (IllegalArgumentException e) {
    System.out.println("Lỗi: " + e.getMessage());
}
```

(Bài 5 sẽ nói kỹ về exception. Ở đây chỉ cần biết `throw` để từ chối và `try / catch` để bắt.)

#### 4. Getter có, setter chưa chắc

Getter cho phép **đọc**. Setter cho phép **ghi tuỳ ý**, tức là mở lại đúng cái cửa mà đóng gói vừa đóng:

```java
public void setBalance(long b) { balance = b; }   // ĐỪNG: phá bất biến
```

Thay setter bằng các method mang **ý nghĩa nghiệp vụ** (`deposit`, `withdraw`). Field không bao giờ đổi sau khi tạo thì đánh dấu `final` và không viết setter.

#### 5. "Tell, don't ask"

```java
// Hỏi rồi tự làm — logic nghiệp vụ nằm rải rác ngoài lớp
if (acc.getBalance() >= amount) { acc.setBalance(acc.getBalance() - amount); }

// Bảo đối tượng tự làm — logic nằm trong Account
acc.withdraw(amount);
```

### Lỗi thường gặp

| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| Field `public` "cho tiện" | Bất biến bị phá từ bên ngoài | `private` + method nghiệp vụ |
| Viết setter cho mọi field | Đóng gói chỉ còn trên danh nghĩa | Chỉ viết method có ý nghĩa |
| Kiểm tra lỗi ở `Main` thay vì trong lớp | Lớp khác gọi thẳng là lọt lỗi | Kiểm tra trong method của lớp |
| Đổi số dư rồi mới kiểm tra | Lỗi xảy ra nhưng dữ liệu đã hỏng | Kiểm tra **trước**, đổi **sau** |

### Trong Mini Bank

Judge kiểm tra `Account` **không có field `public`**. `deposit` phải từ chối số tiền `<= 0` bằng `IllegalArgumentException`, và lỗi được in ra `System.err` để chương trình chạy tiếp.

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

### Tóm tắt

- Field `private`, thay đổi qua method — mọi quy tắc nằm ở một chỗ.
- Method kiểm tra **trước** khi đổi; sai thì `throw` và không đổi gì.
- Getter đọc được; setter phá bất biến — thay bằng method nghiệp vụ.
- Bảo đối tượng tự làm (`withdraw`) thay vì hỏi rồi tự sửa.
