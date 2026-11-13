import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Wallet wallet = new Wallet(sc.nextLine().trim());
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "DEPOSIT" -> {
                        wallet.deposit(Long.parseLong(parts[1]));
                        System.out.println("OK, balance: " + wallet.getBalance());
                    }
                    case "PAY" -> {
                        wallet.pay(Long.parseLong(parts[1]));
                        System.out.println("OK, balance: " + wallet.getBalance());
                    }
                    case "SHOW" -> System.out.println(wallet.getOwner() + ": " + wallet.getBalance());
                    default -> System.err.println("Unknown command: " + parts[0]);
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
