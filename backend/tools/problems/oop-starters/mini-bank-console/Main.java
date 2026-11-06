// Main.java
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Mini Bank");
        System.out.println("=== " + bank.getName() + " ===");

        // Prompt không xuống dòng + nextInt(). Gõ chữ ở đây => InputMismatchException (không bắt) => exit code 1.
        System.out.print("Số tài khoản cần mở: ");
        int n = sc.nextInt();
        sc.nextLine(); // bẫy kinh điển: bỏ phần xuống dòng còn lại sau nextInt()

        for (int i = 1; i <= n; i++) {
            System.out.print("Chủ tài khoản #" + i + ": ");
            String owner = sc.nextLine().trim();
            System.out.print("Loại (S = tiết kiệm, C = thanh toán): ");
            String type = sc.next();
            System.out.print("Số dư ban đầu: ");
            long initial = sc.nextLong();
            sc.nextLine();
            try {
                System.out.println("-> Đã mở " + bank.open(type, owner, initial));
            } catch (IllegalArgumentException e) {
                System.err.println("Lỗi: " + e.getMessage());
            }
        }

        printHelp();
        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) {
                break; // Ctrl+D (EOF)
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" +");
            try {
                switch (p[0].toUpperCase()) {
                    case "HELP" -> printHelp();
                    case "OPEN" -> System.out.println("-> Đã mở " + bank.open(p[1], join(p, 3), Long.parseLong(p[2])));
                    case "DEPOSIT" -> System.out.printf("Số dư mới: %,d%n", bank.find(num(p[1])).deposit(Long.parseLong(p[2])));
                    case "WITHDRAW" -> System.out.printf("Số dư mới: %,d%n", bank.find(num(p[1])).withdraw(Long.parseLong(p[2])));
                    case "TRANSFER" -> {
                        bank.transfer(num(p[1]), num(p[2]), Long.parseLong(p[3]));
                        System.out.println("Đã chuyển.");
                    }
                    case "BALANCE" -> System.out.println(bank.find(num(p[1])));
                    case "LIST" -> bank.getAccounts().forEach(System.out::println);
                    case "MONTH" -> {
                        bank.endOfMonth();
                        System.out.println("Đã tính lãi / phí cuối tháng.");
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
                    default -> System.err.println("Lệnh không hợp lệ: " + p[0] + " (gõ HELP)");
                }
            } catch (InsufficientFundsException | IllegalArgumentException e) {
                System.err.println("Lỗi: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Thiếu tham số cho lệnh " + p[0] + " (gõ HELP)");
            }
        }
        System.out.println();
        System.out.printf("Kết thúc input. Tổng tài sản: %,d%n", bank.totalBalance());
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static String join(String[] parts, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(parts, from, parts.length));
    }

    private static void printHelp() {
        System.out.println("Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>");
        System.out.println("      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]");
    }
}
