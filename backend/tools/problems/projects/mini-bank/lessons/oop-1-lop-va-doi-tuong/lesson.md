> Bài code của chủ đề **Lớp, đối tượng & constructor** (Stage 1 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: danh sách khách hàng
Viết lớp **`Customer`**:

- `private static int nextId = 1` — bộ đếm dùng chung.
- Field `private` cho `id` và `name`.
- Constructor `public Customer(String name)`: lấy `id` từ bộ đếm rồi tăng bộ đếm.
- `getId()`, `getName()`.
- `public static int count()`: số khách hàng đã tạo.
- `toString()` trả về `Customer#<id>: <name>`.

`Main` đọc từ `System.in`:

- Dòng 1: số nguyên `n` (`0 <= n <= 1000`).
- `n` dòng tiếp: mỗi dòng một tên (có thể có khoảng trắng).
- Tạo một `Customer` cho mỗi tên, in từng khách hàng (gọi `toString()`).
- Cuối cùng in `Tổng số khách hàng: <số>` lấy từ `Customer.count()`.

**Ví dụ**

Input:

```
3
An
Bình
Nguyễn Văn Cường
```

Output:

```
Customer#1: An
Customer#2: Bình
Customer#3: Nguyễn Văn Cường
Tổng số khách hàng: 3
```

### Cách chấm
- **Rule cấu trúc** (đọc file `.class`, không chạy code): `Customer` có constructor `public Customer(String)`, field `private static nextId`, method `public static int count()` và `public String toString()`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
