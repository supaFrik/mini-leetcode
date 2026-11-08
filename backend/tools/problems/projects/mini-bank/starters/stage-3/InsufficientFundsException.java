/**
 * Stage 3: checked exception khi rút / chuyển quá hạn mức.
 * Account.withdraw khai báo "throws InsufficientFundsException"; Main bắt lỗi, in ra System.err và chạy tiếp.
 */
public class InsufficientFundsException extends Exception {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
