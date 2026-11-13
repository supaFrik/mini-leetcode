import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Bài 3 — đọc danh sách nhân viên vào MỘT List<Employee>, in từng người và tổng lương. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = Integer.parseInt(sc.nextLine().trim());
        List<Employee> staff = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] p = sc.nextLine().trim().split("\\s+");
            // TODO: E / M / I => thêm new Employee / Manager / Intern vào staff
        }
        // TODO: in từng nhân viên, rồi "Tổng lương: <tổng salary()>"
    }
}
