import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = Integer.parseInt(sc.nextLine().trim());
        for (int i = 0; i < n; i++) {
            System.out.println(new Customer(sc.nextLine().trim()));
        }
        System.out.println("Tổng số khách hàng: " + Customer.count());
    }
}
