import java.util.Scanner;

/** Bài 6 — lệnh ADD / FIND / REMOVE / LIST / COUNT tới hết input. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Library library = new Library();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+", 3);   // tối đa 3 phần: lệnh, mã, phần còn lại là tên sách
            // TODO: xử lý từng lệnh theo bảng trong đề, chỉ dùng method của Library
        }
    }
}
