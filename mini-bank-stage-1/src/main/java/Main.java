import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Mini Bank");
        System.out.println("=== " + bank.getName() + " ===");

        System.out.print("Accounts to open: ");
        int n = sc.nextInt();
        sc.nextLine();

        for (int i = 1; i <= n; i++) {
            System.out.print("Owner #" + i + ": ");
            String owner = sc.nextLine().trim();
            System.out.print("Type (S = savings, C = checking): ");
            String type = sc.next();
            System.out.print("Initial balance: ");
            long initial = sc.nextLong();
            sc.nextLine();
            try {
                System.out.println("-> Opened " + bank.open(type, owner, initial));
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        printHelp();
        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) {
                break;
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" +");
            try {
                switch (p[0].toUpperCase()) {
                    case "DEPOSIT" -> System.out.printf("New balance: %,d%n", bank.find(num(p[1])).deposit(Long.parseLong(p[2])));
                    case "BALANCE" -> System.out.println(bank.find(num(p[1])));
                    case "LIST" -> bank.getAccounts().forEach(System.out::println);
                    case "EXIT" -> {
                        System.out.println("Bye!");
                        System.exit(p.length > 1 ? num(p[1]) : 0);
                    }
                    default -> System.err.println("Unknown command: " + p[0]);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Missing arguments for " + p[0]);
            }
        }
        System.out.println();
        System.out.printf("End of input. Total assets: %,d%n", bank.totalBalance());
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    static void printHelp() {
        System.out.println("Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>");
        System.out.println("          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]");
    }
}
