import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Mini Bank");
        System.out.println("=== " + bank.getName() + " ===");

        // TODO 1: print "Accounts to open: ", read n. For each account ask owner, type (S / C), initial balance;
        //         print "-> Opened " + account.

        printHelp();

        // TODO 2: loop: print "> ", read a command, handle DEPOSIT / BALANCE / LIST / EXIT.
        //         At end of input print an empty line, then "End of input. Total assets: <total>".
    }

    static void printHelp() {
        System.out.println("Commands: OPEN <S|C> <balance> <name> | DEPOSIT <id> <amount> | WITHDRAW <id> <amount>");
        System.out.println("          TRANSFER <from> <to> <amount> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [code]");
    }
}
