> Bài code của chủ đề **Exception: checked, unchecked & exception tự định nghĩa** (Stage 3 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: kiểm tra tuổi
Viết:

- **`InvalidAgeException extends Exception`** với constructor `(String message)` và `(String message, Throwable cause)`.
- **`AgeValidator`** với `public static int validate(String raw) throws InvalidAgeException`:
  - `raw` không phải số nguyên ⇒ ném `InvalidAgeException("Không phải số: <raw>")`, **kèm** `NumberFormatException` làm `cause`.
  - Ngoài khoảng `0..150` ⇒ ném `InvalidAgeException("Tuổi không hợp lệ: <số>")`.
  - Hợp lệ ⇒ trả về tuổi.

`Main` đọc tới hết input. Mỗi dòng: `<tên> <tuổi>` (tên một từ, tuổi là token cuối):

- Hợp lệ: in `<tên>: <tuổi> tuổi`.
- Lỗi: in `<tên>: LỖI - <message>`.
- Cuối cùng in `Hợp lệ: <số dòng hợp lệ>, lỗi: <số dòng lỗi>`.

**Ví dụ**

Input:

```
An 20
Bình abc
Chi 200
Dũng -1
Em 0
```

Output:

```
An: 20 tuổi
Bình: LỖI - Không phải số: abc
Chi: LỖI - Tuổi không hợp lệ: 200
Dũng: LỖI - Tuổi không hợp lệ: -1
Em: 0 tuổi
Hợp lệ: 2, lỗi: 3
```

### Cách chấm
- **Rule cấu trúc**: `InvalidAgeException` kế thừa `Exception` (checked) và có constructor `(String)`; `AgeValidator` có `public static int validate(String)`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
