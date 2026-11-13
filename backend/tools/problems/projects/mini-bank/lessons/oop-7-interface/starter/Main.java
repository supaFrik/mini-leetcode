import java.util.Scanner;

/** Bài 7 — đăng ký lệnh vào Map<String, Command>, tra theo tên thay vì switch. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 1: Map<String, Command> commands = new TreeMap<>(); đăng ký EchoCommand, AddCommand, UpperCommand theo name()
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+", 2);
            String name = p[0];
            String rest = p.length > 1 ? p[1] : "";
            // TODO 2: HELP => "Lệnh: " + tên các lệnh nối bằng ", "
            //         lệnh có trong map => in run(rest); IllegalArgumentException => "Lỗi: " + message
            //         lệnh lạ => "Lệnh không hỗ trợ: " + name
        }
    }
}
