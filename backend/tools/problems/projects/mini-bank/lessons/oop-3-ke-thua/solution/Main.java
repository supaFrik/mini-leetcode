import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = Integer.parseInt(sc.nextLine().trim());
        List<Employee> staff = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] p = sc.nextLine().trim().split("\\s+");
            long base = Long.parseLong(p[2]);
            switch (p[0]) {
                case "M" -> staff.add(new Manager(p[1], base, Long.parseLong(p[3])));
                case "I" -> staff.add(new Intern(p[1], base));
                default -> staff.add(new Employee(p[1], base));
            }
        }
        long total = 0;
        for (Employee e : staff) {
            System.out.println(e);
            total += e.salary();
        }
        System.out.println("Tổng lương: " + total);
    }
}
