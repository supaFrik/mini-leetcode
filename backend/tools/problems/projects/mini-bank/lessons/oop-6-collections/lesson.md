> Bài code của chủ đề **Collections & đóng gói danh sách** (Stage 4 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: thư viện sách
Viết:

- **`Book`**: `id` và `title` (`private final`), getter, `toString()` trả `<id>: <title>`.
- **`Library`**: giữ sách trong một collection `private` (gợi ý `LinkedHashMap<String, Book>`), **mọi field `private`**:
  - `public boolean add(Book book)`: `false` nếu trùng mã.
  - `public Book find(String id)`: `null` nếu không có.
  - `public boolean remove(String id)`.
  - `public List<Book> all()`: danh sách theo thứ tự thêm, **không sửa được** từ bên ngoài.
  - `public int size()`.

`Main` đọc tới hết input, mỗi dòng một lệnh:

| Lệnh | Output |
|------|--------|
| `ADD <mã> <tên sách...>` | `Đã thêm <mã>` hoặc `Trùng mã <mã>` |
| `FIND <mã>` | `<mã>: <tên>` hoặc `Không tìm thấy <mã>` |
| `REMOVE <mã>` | `Đã xoá <mã>` hoặc `Không tìm thấy <mã>` |
| `LIST` | mỗi sách một dòng `<mã>: <tên>` theo thứ tự thêm; rỗng thì `(trống)` |
| `COUNT` | `Số sách: <n>` |

**Ví dụ**

Input:

```
ADD B1 Clean Code
ADD B2 Effective Java
ADD B1 Refactoring
FIND B2
REMOVE B9
LIST
COUNT
```

Output:

```
Đã thêm B1
Đã thêm B2
Trùng mã B1
B2: Effective Java
Không tìm thấy B9
B1: Clean Code
B2: Effective Java
Số sách: 2
```

### Cách chấm
- **Rule cấu trúc**: `Library` đóng gói (mọi field `private`) và có `add(Book)`, `find(String)`, `all()` trả `List`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
