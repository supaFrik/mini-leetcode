## Mục tiêu
- Dùng `extends` để tạo lớp con dùng lại code của lớp cha.
- Gọi constructor lớp cha bằng `super(...)`.
- **Ghi đè** method với `@Override` và gọi lại bản của lớp cha bằng `super.method()`.
- Biết khi nào kế thừa là sai lựa chọn.

## 1. Quan hệ "là một" (is-a)

Tài khoản tiết kiệm **là một** tài khoản. Quản lý **là một** nhân viên. Khi câu "B là một A" đúng về nghiệp vụ, `B extends A` là hợp lý: B có mọi field và method của A, rồi thêm hoặc thay đổi một phần.

```java
public class Employee {
    private final String name;
    protected final long baseSalary;   // lớp con đọc được

    public Employee(String name, long baseSalary) {
        this.name = name;
        this.baseSalary = baseSalary;
    }

    public long salary() { return baseSalary; }
    public String getName() { return name; }
}

public class Manager extends Employee {
    private final long bonus;

    public Manager(String name, long baseSalary, long bonus) {
        super(name, baseSalary);   // PHẢI là câu lệnh đầu tiên
        this.bonus = bonus;
    }
}
```

- Một lớp Java chỉ `extends` **một** lớp cha (đơn kế thừa).
- Mọi lớp đều ngầm `extends Object`.
- Field `private` của cha **vẫn tồn tại** trong đối tượng con nhưng con không truy cập trực tiếp được; dùng method của cha hoặc để `protected`.

## 2. `super(...)`: lớp cha được khởi tạo trước

Constructor lớp con luôn gọi constructor lớp cha trước tiên. Không viết `super(...)` thì Java tự chèn `super()` — nếu cha không có constructor rỗng, code **không biên dịch**. Đây là lỗi hay gặp nhất khi mới kế thừa.

## 3. Ghi đè (override)

Lớp con viết lại method cùng tên, cùng tham số để **thay hành vi**:

```java
public class Manager extends Employee {
    // ...
    @Override
    public long salary() {
        return super.salary() + bonus;   // dùng lại bản của cha rồi cộng thêm
    }
}
```

Khi gọi `e.salary()`, Java chọn method theo **kiểu thật của đối tượng** lúc chạy, không theo kiểu biến:

```java
Employee e = new Manager("Hà", 20_000_000, 5_000_000);
System.out.println(e.salary());   // 25000000 — bản của Manager
```

Đây chính là **đa hình** (bài 4 đi sâu hơn).

Quy tắc ghi đè:

- Cùng tên, cùng danh sách tham số. Khác tham số là **overload** (method mới), không phải override.
- Không được thu hẹp quyền truy cập (cha `public` thì con cũng `public`).
- Method `final` không ghi đè được; method `static` và `private` không tham gia ghi đè.

## 4. Template: lớp cha định khung, lớp con điền chi tiết

```java
public class Employee {
    public String role() { return "Nhân viên"; }

    @Override
    public String toString() {
        return getName() + " (" + role() + "): " + salary();
    }
}
```

`toString()` viết **một lần** ở lớp cha nhưng in đúng cho mọi lớp con, vì `role()` và `salary()` được ghi đè.

## 5. Khi nào không nên kế thừa

- "B **có một** A" (ngân hàng có danh sách tài khoản) ⇒ dùng **field** (composition), không `extends`.
- Chỉ muốn dùng lại vài method tiện ích ⇒ gọi qua đối tượng khác, đừng kế thừa.
- Lớp con phải "vô hiệu hoá" method của cha (ném lỗi khi gọi) ⇒ dấu hiệu quan hệ is-a sai.

## Lỗi thường gặp
| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| Quên `super(...)` khi cha không có constructor rỗng | Lỗi biên dịch | Gọi `super(...)` đầu constructor |
| Đổi tham số khi "ghi đè" | Thành overload, không chạy bản mới | Giữ nguyên tham số + `@Override` |
| Sao chép code cha sang con | Sửa một chỗ quên chỗ kia | Gọi `super.method()` |
| Kế thừa cho quan hệ "có một" | Thiết kế gượng ép | Dùng field |

## Trong Mini Bank
Stage 2 tách `Account` thành `SavingsAccount` và `CheckingAccount`. Mỗi lớp có quy tắc rút tiền và lãi / phí cuối tháng riêng, còn phần chung (id, chủ, số dư, `toString()`) nằm ở lớp cha.

## Tóm tắt
- `extends` khi "B là một A"; Java chỉ cho một lớp cha.
- Constructor con gọi `super(...)` đầu tiên.
- Ghi đè = cùng chữ ký + `@Override`; `super.method()` dùng lại bản của cha.
- Method được chọn theo kiểu **thật** của đối tượng lúc chạy.
- "Có một" ⇒ composition, không kế thừa.
