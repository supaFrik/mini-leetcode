import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int valid = 0;
        int invalid = 0;
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+");
            String name = p[0];
            String raw = p[p.length - 1];
            try {
                int age = AgeValidator.validate(raw);
                System.out.println(name + ": " + age + " tuổi");
                valid++;
            } catch (InvalidAgeException e) {
                System.out.println(name + ": LỖI - " + e.getMessage());
                invalid++;
            }
        }
        System.out.println("Hợp lệ: " + valid + ", lỗi: " + invalid);
    }
}
