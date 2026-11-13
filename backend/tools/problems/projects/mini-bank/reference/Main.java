// Main.java
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Mini Bank");
        System.out.println("=== " + bank.getName() + " ===");

        // Prompts use print (no newline). A non-number here throws InputMismatchException (exit code 1).
        System.out.print("Accounts to open: ");
        int n = sc.nextInt();
        sc.nextLine(); // consume the rest of the line after nextInt()

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
                break; // end of input
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" +");
            try {
                switch (p[0].toUpperCase()) {
                    case "HELP" -> printHelp();
                    case "OPEN" -> System.out.println("-> Opened " + bank.open(p[1], join(p, 3), Long.parseLong(p[2])));
                    case "DEPOSIT" -> System.out.printf("New balance: %,d%n", bank.find(num(p[1])).deposit(Long.parseLong(p[2])));
                    case "WITHDRAW" -> System.out.printf("New balance: %,d%n", bank.find(num(p[1])).withdraw(Long.parseLong(p[2])));
                    case "TRANSFER" -> {
                        bank.transfer(num(p[1]), num(p[2]), Long.parseLong(p[3]));
                        System.out.println("Transferred.");
                    }
                    case "BALANCE" -> System.out.println(bank.find(num(p[1])));
                    case "LIST" -> bank.getAccounts().forEach(System.out::println);
                    case "MONTH" -> {
                        bank.endOfMonth();
                        System.out.println("Month-end interest and fees applied.");
                    }
                    case "REPORT" -> {
                        int lines = num(p[1]);
                        for (int i = 1; i <= lines; i++) {
                            System.out.println("Report line " + i + " - total " + bank.totalBalance());
                        }
                    }
                    case "EXIT" -> {
                        System.out.println("Bye!");
                        System.exit(p.length > 1 ? num(p[1]) : 0);
                    }
                    default -> System.err.println("Unknown command: " + p[0] + " (type HELP)");
                }
            } catch (InsufficientFundsException | IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Missing arguments for " + p[0] + " (type HELP)");
            }
        }
        System.out.println();
        System.out.printf("End of input. Total assets: %,d%n", bank.totalBalance());
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static String join(String[] parts, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(parts, from, parts.length));
    }

    private static void printHelp() {
        System.out.println("Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>");
        System.out.println("          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]");
    }
}
