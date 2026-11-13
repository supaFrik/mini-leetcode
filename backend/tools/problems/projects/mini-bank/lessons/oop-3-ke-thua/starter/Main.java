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
            // TODO: E / M / I -> add new Employee / Manager / Intern
        }
        // TODO: print each employee, then "Total salary: <sum of salary()>"
    }
}
