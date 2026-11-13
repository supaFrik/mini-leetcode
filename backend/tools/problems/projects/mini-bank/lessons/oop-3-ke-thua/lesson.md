> Bài code của chủ đề **Kế thừa & ghi đè (override)** (Stage 2 của plan *OOP với Java: xây Mini Bank*). Học phần lý thuyết và làm các câu luyện tập của chủ đề trước, rồi giải bài này.

### Bài tập: bảng lương
Viết ba lớp:

- **`Employee`**: field tên và lương cơ bản; constructor `Employee(String name, long baseSalary)`; `public long salary()` trả lương cơ bản; `public String role()` trả `Nhân viên`; `toString()` trả `<tên> (<role>): <salary>`.
- **`Manager extends Employee`**: thêm `bonus`; constructor `Manager(String name, long baseSalary, long bonus)`; `salary()` = lương cơ bản + thưởng (dùng `super.salary()`); `role()` = `Quản lý`.
- **`Intern extends Employee`**: `salary()` = lương cơ bản / 2 (chia nguyên); `role()` = `Thực tập`.

`Main` đọc:

- Dòng 1: `n`.
- `n` dòng: `E <tên> <lương>`, `M <tên> <lương> <thưởng>` hoặc `I <tên> <lương>` (tên một từ).
- Lưu tất cả vào **một** `List<Employee>`, in từng người, rồi in `Tổng lương: <tổng salary()>`.

**Ví dụ**

Input:

```
3
E An 10000000
M Hà 20000000 5000000
I Tú 6000000
```

Output:

```
An (Nhân viên): 10000000
Hà (Quản lý): 25000000
Tú (Thực tập): 3000000
Tổng lương: 38000000
```

### Cách chấm
- **Rule cấu trúc**: `Employee` có `public long salary()` và `public String role()`; `Manager` và `Intern` kế thừa `Employee` và tự cài `salary()`.
- **Testcase stdin/stdout**: so từng dòng, bỏ qua khoảng trắng thừa.
