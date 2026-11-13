## Mục tiêu
- Hiểu interface là **hợp đồng**: nói *làm gì*, không nói *làm thế nào*.
- Phân biệt interface với lớp trừu tượng.
- Lập trình theo interface: code phụ thuộc vào hợp đồng, không phụ thuộc lớp cụ thể.
- Thay chuỗi `if / switch` dài bằng một **bảng lệnh** `Map<String, Command>`.

## 1. Interface là hợp đồng

```java
public interface Command {
    String name();              // mặc định public abstract
    String run(String args);
}
```

Lớp nào `implements Command` thì **cam kết** có hai method đó:

```java
public class EchoCommand implements Command {
    @Override public String name() { return "ECHO"; }
    @Override public String run(String args) { return args; }
}
```

Nơi dùng chỉ cần biết `Command`, không cần biết `EchoCommand` tồn tại.

## 2. Interface hay lớp trừu tượng?

| | Interface | Lớp trừu tượng |
|---|-----------|----------------|
| Một lớp có thể có | **nhiều** (`implements A, B`) | **một** (`extends`) |
| Field trạng thái | Không (chỉ hằng `static final`) | Có |
| Constructor | Không | Có |
| Method có thân | `default`, `static`, `private` | Bất kỳ |
| Dùng khi | Mô tả **khả năng** chung cho các lớp không liên quan | Chia sẻ **trạng thái + code** giữa các lớp cùng họ |

Mini Bank dùng cả hai: `Account` là lớp trừu tượng (các tài khoản chung id, chủ, số dư), còn "một lệnh CLI" hợp làm interface (các lệnh chẳng có trạng thái chung nào).

## 3. Method `default`

```java
public interface Command {
    String name();
    String run(String args);

    default String usage() {            // có thân: lớp cài không bắt buộc ghi đè
        return name() + " <tham số>";
    }
}
```

`default` cho phép thêm method vào interface mà không làm hỏng các lớp đã cài.

## 4. Bảng lệnh thay cho `switch`

```java
// Trước: mỗi lệnh mới là thêm một nhánh, hàm main dài mãi
switch (cmd) {
    case "ECHO" -> ...;
    case "ADD"  -> ...;
    // ...
}

// Sau: đăng ký một lần, tra theo tên
Map<String, Command> commands = new TreeMap<>();
for (Command c : List.of(new EchoCommand(), new AddCommand())) {
    commands.put(c.name(), c);
}

Command c = commands.get(name);
if (c == null) {
    System.out.println("Lệnh không hỗ trợ: " + name);
} else {
    System.out.println(c.run(args));    // đa hình qua interface
}
```

Thêm lệnh mới = viết **một lớp** và đăng ký nó. Không sửa vòng lặp chính. Đây là mẫu thiết kế **Command**.

## 5. Interface hàm và lambda (xem trước)

Interface chỉ có **một** method trừu tượng là *functional interface*; có thể tạo nhanh bằng lambda:

```java
interface Action { String run(String args); }
Action upper = args -> args.toUpperCase();
```

Bài tập dưới đây yêu cầu viết lớp đầy đủ để luyện `implements`; lambda là bước tiếp theo khi bạn đã quen.

## Lỗi thường gặp
| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| Quên `public` khi cài method của interface | Lỗi "attempting to assign weaker access" | Method cài phải `public` |
| Khai báo biến bằng lớp cụ thể (`EchoCommand c`) | Mất lợi ích thay thế | Khai báo bằng interface (`Command c`) |
| Nhét trạng thái vào interface | Interface chỉ có hằng | Đưa trạng thái vào lớp cài |
| `HashMap` khi cần in danh sách lệnh theo thứ tự | Thứ tự lộn xộn | `TreeMap` (sắp xếp theo tên) |

## Trong Mini Bank
Stage 5 thêm `HELP`, `REPORT <n>`, `EXIT [mã]` vào một CLI vốn đã có nhiều lệnh. Tách mỗi lệnh thành một lớp `Command` giữ `Main` ngắn gọn và dễ thêm lệnh — judge chỉ chấm output, nhưng đây là cách tổ chức được khuyến khích.

## Tóm tắt
- Interface = hợp đồng; một lớp cài được nhiều interface.
- Lớp trừu tượng chia sẻ trạng thái + code; interface mô tả khả năng.
- Khai báo bằng interface, tạo bằng lớp cụ thể.
- `Map<String, Command>` thay chuỗi `switch`: thêm lệnh = thêm lớp.
