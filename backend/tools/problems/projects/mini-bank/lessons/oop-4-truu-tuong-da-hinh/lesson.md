> Bài code của chủ đề **Lớp trừu tượng & đa hình** (Stage 2 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

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
