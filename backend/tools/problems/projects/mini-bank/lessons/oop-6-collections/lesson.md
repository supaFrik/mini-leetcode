> Kiến thức cho **Stage 4** của project Mini Bank. Đọc phần lý thuyết, rồi làm bài tập ở cuối trang.

### Mục tiêu

- Chọn đúng `List`, `Set`, `Map` cho từng nhu cầu.
- Dùng **generics** (`List<Account>`) để trình biên dịch kiểm tra kiểu.
- Tìm theo khoá bằng `Map` thay vì duyệt cả danh sách.
- **Không để lộ** collection bên trong đối tượng ra ngoài.

### Lý thuyết

#### 1. Ba interface chính

| Interface | Đặc điểm | Cài đặt hay dùng |
|-----------|----------|------------------|
| `List<E>` | Có thứ tự, cho phép trùng, truy cập theo chỉ số | `ArrayList` |
| `Set<E>` | Không trùng | `HashSet` (không thứ tự), `LinkedHashSet` (giữ thứ tự thêm), `TreeSet` (sắp xếp) |
| `Map<K, V>` | Cặp khoá → giá trị, khoá không trùng | `HashMap`, `LinkedHashMap` (giữ thứ tự thêm), `TreeMap` |

Khai báo biến bằng **interface**, tạo bằng **lớp cài đặt**:

```java
List<Account> accounts = new ArrayList<>();     // đổi sang LinkedList sau này không phải sửa chỗ dùng
Map<Integer, Account> byId = new LinkedHashMap<>();
```

#### 2. Generics: kiểu an toàn lúc biên dịch

```java
List<Account> accounts = new ArrayList<>();
accounts.add(new SavingsAccount(...));   // được: SavingsAccount là Account
accounts.add("hello");                   // LỖI biên dịch
Account first = accounts.get(0);         // không cần ép kiểu
```

Collection chỉ chứa **đối tượng**: số nguyên dùng `Integer`, `Long` (Java tự đóng / mở hộp — autoboxing).

#### 3. Tìm theo khoá: `Map` thay vì vòng lặp

```java
// Duyệt cả danh sách: chậm dần khi nhiều tài khoản
for (Account a : accounts) if (a.getId() == id) return a;

// Map: tra trực tiếp
Account a = byId.get(id);        // null nếu không có
byId.containsKey(id);            // kiểm tra tồn tại
byId.putIfAbsent(id, account);   // chỉ thêm khi chưa có
byId.remove(id);                 // trả về giá trị đã xoá hoặc null
```

`LinkedHashMap` vừa tra nhanh vừa **giữ thứ tự thêm vào** — hợp với "in danh sách theo thứ tự mở".

#### 4. Đóng gói collection: đừng trả về chính danh sách bên trong

```java
public class Bank {
    private final List<Account> accounts = new ArrayList<>();

    public List<Account> getAccounts() {
        return accounts;              // NGUY HIỂM: nơi gọi .clear() là mất sạch dữ liệu
    }
}
```

`private` chỉ ngăn truy cập **field**; trả tham chiếu ra ngoài là mở toang cửa. Hai cách an toàn:

```java
return List.copyOf(accounts);                     // bản sao không sửa được
return Collections.unmodifiableList(accounts);    // "cửa sổ chỉ đọc" nhìn vào danh sách thật
```

Mọi thay đổi phải đi qua method của `Bank` (`open`, `close`...) — đúng tinh thần bài 2.

#### 5. Duyệt và sửa

```java
for (Account a : accounts) { ... }             // duyệt
accounts.removeIf(a -> a.getBalance() == 0);   // xoá theo điều kiện
```

Xoá phần tử **trong** vòng `for-each` sẽ ném `ConcurrentModificationException`; dùng `removeIf` hoặc `Iterator.remove()`.

### Lỗi thường gặp

| Lỗi | Hậu quả | Sửa |
|-----|---------|-----|
| Getter trả về collection bên trong | Bên ngoài sửa được dữ liệu | `List.copyOf` / `unmodifiableList` |
| `HashMap` khi cần in theo thứ tự thêm | Thứ tự in lộn xộn | `LinkedHashMap` |
| Xoá trong `for-each` | `ConcurrentModificationException` | `removeIf` |
| Quên kiểm tra `null` sau `map.get` | `NullPointerException` | `containsKey` hoặc kiểm tra `null` |

### Trong Mini Bank

Stage 4 yêu cầu `Bank` **đóng gói** danh sách tài khoản (field `private`, `Main` không sửa trực tiếp) với `open`, `find`, `transfer`. Judge kiểm tra encapsulation của `Bank` bằng reflection.

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

### Tóm tắt

- `List` có thứ tự, `Set` không trùng, `Map` tra theo khoá; khai báo bằng interface.
- Generics cho kiểm tra kiểu lúc biên dịch.
- Tìm theo id ⇒ `Map`; cần giữ thứ tự thêm ⇒ `LinkedHashMap`.
- Không trả collection bên trong ra ngoài: dùng bản sao / bản chỉ đọc.
