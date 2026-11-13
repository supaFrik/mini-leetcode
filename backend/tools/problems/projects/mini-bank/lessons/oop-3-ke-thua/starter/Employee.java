/** Bài 3 — lớp cha của mọi nhân viên. Đề: lesson.md. */
public class Employee {
    // TODO 1: field tên (private) và lương cơ bản (protected để lớp con đọc được)

    public Employee(String name, long baseSalary) {
        // TODO 2
    }

    public long salary() {
        return 0; // TODO 3: lương cơ bản
    }

    public String role() {
        return "Nhân viên";
    }

    @Override
    public String toString() {
        return ""; // TODO 4: "<tên> (<role()>): <salary()>" — viết một lần, đúng cho mọi lớp con
    }
}
