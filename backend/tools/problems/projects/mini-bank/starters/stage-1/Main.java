import java.util.Scanner;

/**
 * Mini Bank — Stage 1. Đề: stages/stage-1.md.
 * Judge so stdout từng dòng (bỏ qua khoảng trắng thừa và dòng trống, nội dung phải đúng); lỗi in ra System.err không bị so.
 */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank("Mini Bank");
        System.out.println("=== " + bank.getName() + " ===");

        // TODO 1: in "Số tài khoản cần mở: " rồi đọc n.
        //         Với mỗi tài khoản i: hỏi chủ, loại (S / C), số dư ban đầu; in "-> Đã mở " + tài khoản.

        printHelp();

        // TODO 2: lặp: in "> ", đọc một dòng lệnh, xử lý DEPOSIT / BALANCE / LIST / EXIT.
        //         Hết input (không còn dòng nào) thì in một dòng trống rồi
        //         "Kết thúc input. Tổng tài sản: <tổng>".
    }

    static void printHelp() {
        System.out.println("Lệnh: OPEN <S|C> <số dư> <tên> | DEPOSIT <id> <tiền> | WITHDRAW <id> <tiền>");
        System.out.println("      TRANSFER <từ> <đến> <tiền> | BALANCE <id> | LIST | MONTH | REPORT <n> | EXIT [mã]");
    }
}
