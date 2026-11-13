> Kiến thức cho **Stage 2** của project Mini Bank. Đọc phần lý thuyết, rồi làm bài tập ở cuối trang.

### Mục tiêu

- Khai báo **lớp trừu tượng** và **method trừu tượng**.
- Hiểu **đa hình**: một lời gọi, nhiều cách chạy tuỳ đối tượng thật.
- Phân biệt **kiểu khai báo** (static type) và **kiểu thật** (dynamic type).
- Viết code xử lý "mọi loại" mà không cần `if / instanceof`.

### Lý thuyết

#### 1. Có những thứ không nên tạo trực tiếp

"Một hình" chung chung thì diện tích bao nhiêu? Không trả lời được — chỉ hình tròn, hình chữ nhật cụ thể mới tính được. Lớp như vậy nên là `abstract`:

```java
public abstract class Shape {
    public abstract double area();     // không có thân: lớp con BẮT BUỘC cài
    public abstract String name();

    public String describe() {         // method bình thường, dùng các method trừu tượng
        return String.format(java.util.Locale.ROOT, "%s: %.2f", name(), area());
    }
}

new Shape();   // LỖI biên dịch: không tạo được đối tượng của lớp trừu tượng
```

- Lớp có ít nhất một method `abstract` thì lớp phải `abstract`.
- Lớp trừu tượng **vẫn có** field, constructor, method thường — nó là lớp cha "chưa hoàn chỉnh".
- Lớp con cụ thể phải cài **mọi** method trừu tượng, nếu không nó cũng phải `abstract`.

```java
public class Circle extends Shape {
    private final double r;
    public Circle(double r) { this.r = r; }

    @Override public double area() { return Math.PI * r * r; }
    @Override public String name() { return "Hình tròn"; }
}
```

#### 2. Đa hình: kiểu khai báo vs kiểu thật

```java
Shape s = new Circle(1);   // kiểu khai báo: Shape, kiểu thật: Circle
s.area();                  // chạy Circle.area()
```

- **Trình biên dịch** dựa vào kiểu khai báo để quyết định bạn **được gọi** method nào (`Shape` có `area()` ⇒ hợp lệ).
- **Lúc chạy**, JVM dựa vào kiểu thật để chọn **bản nào chạy** (dynamic dispatch).

Nhờ vậy một vòng lặp xử lý được mọi loại hình, kể cả loại thêm sau này:

```java
List<Shape> shapes = List.of(new Circle(1), new Rectangle(2, 3));
for (Shape s : shapes) {
    System.out.println(s.describe());   // mỗi đối tượng tự biết tính diện tích của mình
}
```

#### 3. Thay `if / instanceof` bằng đa hình

```java
// Cách cũ: thêm một loại hình là phải sửa mọi chỗ như thế này
if (s instanceof Circle c) { ... } else if (s instanceof Rectangle r) { ... }

// Đa hình: thêm loại mới = thêm một lớp con, không sửa code đang chạy
total += s.area();
```

Đây là tinh thần của nguyên tắc **mở để mở rộng, đóng để sửa đổi** (Open/Closed).

#### 4. Template method

`describe()` ở trên là một **template method**: lớp cha cố định **khung** (định dạng chuỗi), lớp con chỉ điền **chi tiết** (`name()`, `area()`). Stage 2 của Mini Bank dùng đúng mẫu này: `Account.withdraw()` viết ở lớp cha, gọi `canWithdraw()` trừu tượng do từng loại tài khoản quyết định.

#### 5. Kế thừa nhiều tầng

```java
public class Square extends Rectangle {
    public Square(double a) { super(a, a); }
    @Override public String name() { return "Hình vuông"; }
}
```

`Square` là `Rectangle`, cũng là `Shape`: dùng được ở mọi chỗ cần một trong hai kiểu.

### Lỗi thường gặp

| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| Cài method trừu tượng sai chữ ký | Lỗi "is not abstract and does not override..." | Giữ đúng tên, tham số, kiểu trả về; thêm `@Override` |
| Dùng `instanceof` để chọn công thức | Thêm loại mới phải sửa nhiều chỗ | Đưa công thức vào method của từng lớp |
| `String.format("%.2f")` không có `Locale` | Máy cài tiếng Việt in `3,14` thay vì `3.14` | `String.format(Locale.ROOT, ...)` |
| Để lớp cha cụ thể khi nó không có nghĩa tự đứng | Tạo được đối tượng "vô nghĩa" | Đánh dấu `abstract` |

### Trong Mini Bank

Judge kiểm tra `Account` là **lớp trừu tượng** với `canWithdraw(long)`, `endOfMonth()`, `getType()` trừu tượng. Lệnh `MONTH` chỉ cần một vòng lặp gọi `endOfMonth()` cho mọi tài khoản: tiết kiệm cộng lãi, thanh toán trừ phí.

### Bài tập: diện tích các hình

Viết:

- **`abstract class Shape`** với `public abstract double area()`, `public abstract String name()` và `public String describe()` trả `<name>: <diện tích>` (2 chữ số thập phân, `Locale.ROOT`).
- **`Circle extends Shape`** (`Hình tròn`, bán kính `r`).
- **`Rectangle extends Shape`** (`Hình chữ nhật`, `w × h`).
- **`Square extends Rectangle`** (`Hình vuông`, cạnh `a`).

`Main` đọc tới hết input, mỗi dòng một hình: `CIRCLE r`, `RECT w h`, `SQUARE a` (số thực, `>= 0`). Lưu vào **một** `List<Shape>`, in `describe()` của từng hình, rồi in `Lớn nhất: <describe() của hình có diện tích lớn nhất>` (hoà thì lấy hình xuất hiện trước). Không có hình nào thì chỉ in `Không có hình`.

**Ví dụ**

Input:

```
CIRCLE 1
RECT 2 3
SQUARE 2.5
```

Output:

```
Hình tròn: 3.14
Hình chữ nhật: 6.00
Hình vuông: 6.25
Lớn nhất: Hình vuông: 6.25
```

### Cách chấm

- **Rule cấu trúc**: `Shape` là lớp trừu tượng với `area()` và `name()` trừu tượng; `Circle`, `Rectangle` kế thừa `Shape`; `Square` kế thừa `Rectangle`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.

### Tóm tắt

- `abstract class` = lớp cha chưa hoàn chỉnh, không `new` được; method `abstract` buộc lớp con cài.
- Biến kiểu cha trỏ được tới đối tượng con; method chạy theo kiểu **thật**.
- Đa hình thay cho chuỗi `if / instanceof`; thêm loại mới không phải sửa code cũ.
- Template method: cha định khung, con điền chi tiết.
