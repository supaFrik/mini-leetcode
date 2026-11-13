## Mục tiêu
- Phân biệt **lớp** (class) và **đối tượng** (object).
- Viết **field**, **method**, **constructor** và dùng `this`.
- Hiểu biến **tham chiếu** (reference) và khác biệt giữa thành viên `static` với thành viên của từng đối tượng.
- Ghi đè `toString()` để in đối tượng cho dễ đọc.

## 1. Lớp là bản thiết kế, đối tượng là sản phẩm

Lớp mô tả một **loại** thứ: nó có **trạng thái** gì (field) và **làm được gì** (method). Đối tượng là một thứ cụ thể được tạo ra từ bản thiết kế đó bằng `new`.

```java
public class Account {
    String owner;      // field: trạng thái
    long balance;

    void deposit(long amount) {   // method: hành vi
        balance += amount;
    }
}

Account a = new Account();   // một đối tượng
Account b = new Account();   // một đối tượng KHÁC, có balance riêng
a.deposit(100);              // chỉ balance của a đổi
```

Mỗi đối tượng giữ **bản sao riêng** của các field. `a.balance` và `b.balance` là hai ô nhớ khác nhau.

## 2. Constructor: tạo đối tượng ở trạng thái hợp lệ ngay từ đầu

Constructor chạy đúng một lần khi `new`. Tên trùng tên lớp, không có kiểu trả về.

```java
public class Account {
    private final String owner;
    private long balance;

    public Account(String owner, long initial) {
        this.owner = owner;       // this.owner là field, owner là tham số
        this.balance = initial;
    }
}
```

- `this` trỏ tới **chính đối tượng đang được tạo / đang gọi method**. Dùng `this.x = x` khi tham số trùng tên field.
- Nếu bạn không viết constructor nào, Java tự thêm constructor rỗng `Account()`. Viết một constructor có tham số thì constructor rỗng **biến mất**.
- Có thể có nhiều constructor (overloading), constructor này gọi constructor kia bằng `this(...)`.

## 3. Biến lưu tham chiếu, không lưu đối tượng

```java
Account x = new Account("An", 0);
Account y = x;          // y trỏ tới CÙNG đối tượng với x
y.deposit(50);
System.out.println(x.getBalance());   // 50 — vì x và y là một
```

`==` so sánh hai tham chiếu có trỏ cùng đối tượng không, **không** so nội dung. Muốn so nội dung thì dùng `equals` (chuỗi luôn so bằng `equals`).

## 4. `static`: thuộc về lớp, không thuộc về từng đối tượng

Field `static` chỉ có **một bản** dùng chung cho cả lớp. Đây là cách tự nhiên để cấp mã tự tăng:

```java
public class Account {
    private static int nextId = 1;   // dùng chung
    private final int id;            // mỗi đối tượng một id

    public Account() {
        this.id = nextId++;          // lấy mã hiện tại rồi tăng
    }

    public static int created() {    // method static: gọi bằng tên lớp
        return nextId - 1;
    }
}

new Account(); new Account();
System.out.println(Account.created());   // 2
```

Method `static` không có `this`, nên không đọc được field của đối tượng nào.

## 5. `toString()`: đối tượng tự mô tả mình

Mọi lớp kế thừa từ `Object`. `Object.toString()` in ra kiểu `Account@1b6d3586`, chẳng có ích gì. Ghi đè nó:

```java
@Override
public String toString() {
    return "#" + id + " " + owner;
}

System.out.println(account);   // println tự gọi toString()
```

`@Override` nhờ trình biên dịch kiểm tra bạn thật sự ghi đè (gõ sai `tostring` sẽ báo lỗi ngay).

## Lỗi thường gặp
| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| `owner = owner;` trong constructor | Gán tham số cho chính nó, field vẫn `null` | `this.owner = owner;` |
| `nextId` không `static` | Mọi đối tượng đều có id `1` | Đặt `static` cho bộ đếm |
| So chuỗi bằng `==` | Lúc đúng lúc sai | Dùng `equals` |
| Quên `@Override`, gõ sai tên | In ra `Customer@...` | Luôn thêm `@Override` |

## Trong Mini Bank
Stage 1 cần lớp `Account` có **id tự tăng từ 1**, chủ tài khoản, số dư và `toString()` định dạng sẵn. Bài tập dưới đây luyện đúng ba thứ đó trên một lớp nhỏ hơn.

## Tóm tắt
- Lớp = bản thiết kế, đối tượng = sản phẩm tạo bằng `new`; mỗi đối tượng có field riêng.
- Constructor đưa đối tượng về trạng thái hợp lệ; `this` là đối tượng hiện tại.
- Biến giữ **tham chiếu**; gán biến không sao chép đối tượng.
- `static` = dùng chung cho cả lớp — hợp với bộ đếm id.
- Ghi đè `toString()` (kèm `@Override`) để in đối tượng.
