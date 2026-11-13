import java.util.Scanner;

/** Bài 2 — đọc tên chủ ví rồi các lệnh NAP / TRA / XEM tới hết input. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Wallet wallet = new Wallet(sc.nextLine().trim());
        while (sc.hasNextLine()) {
            String[] parts = sc.nextLine().trim().split("\\s+");
            // TODO: NAP <tiền> / TRA <tiền> => gọi deposit / pay, in "OK, số dư: <số dư>"
            //       bắt IllegalArgumentException => in "Lỗi: " + e.getMessage()
            //       XEM => in "<chủ ví>: <số dư>"
        }
    }
}
