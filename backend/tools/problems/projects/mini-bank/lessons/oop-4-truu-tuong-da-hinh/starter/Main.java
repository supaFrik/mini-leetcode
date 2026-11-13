import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Bài 4 — đọc các hình tới hết input vào MỘT List<Shape>. Đề: lesson.md. */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        List<Shape> shapes = new ArrayList<>();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            // TODO: CIRCLE r / RECT w h / SQUARE a => thêm hình vào shapes (Double.parseDouble)
        }
        // TODO: in describe() từng hình; rồi "Lớn nhất: " + describe() của hình lớn nhất (hoà lấy hình trước)
        //       không có hình nào => "Không có hình"
    }
}
