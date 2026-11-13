import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Wallet wallet = new Wallet(sc.nextLine().trim());
        while (sc.hasNextLine()) {
            String[] parts = sc.nextLine().trim().split("\\s+");
            // TODO: DEPOSIT / PAY -> "OK, balance: <balance>"; catch IllegalArgumentException -> "Error: " + message
            //       SHOW -> "<owner>: <balance>"
        }
    }
}
