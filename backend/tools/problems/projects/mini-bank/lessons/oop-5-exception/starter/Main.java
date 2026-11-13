import java.util.Scanner;

/** Bài 5 — mỗi dòng "<tên> <tuổi>"; lỗi ở một dòng không được làm dừng chương trình. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int valid = 0;
        int invalid = 0;
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            String name = p[0];
            String raw = p[p.length - 1];
            // TODO: try { AgeValidator.validate(raw) => in "<tên>: <tuổi> tuổi" }
            //       catch (InvalidAgeException e) { in "<tên>: LỖI - " + e.getMessage() }
        }
        System.out.println("Hợp lệ: " + valid + ", lỗi: " + invalid);
    }
}
