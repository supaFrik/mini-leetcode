/**
 * Một tài khoản ngân hàng. Stage 1: chưa cần kế thừa — lưu loại tài khoản ("SAVINGS" / "CHECKING") trong một field.
 * Không field nào được để public (judge kiểm tra).
 */
public class Account {
    // TODO: id tự tăng bắt đầu từ 1, loại, chủ tài khoản, số dư

    public Account(String type, String owner, long initialBalance) {
        // TODO
    }

    public int getId() {
        return 0; // TODO
    }

    public long getBalance() {
        return 0; // TODO
    }

    /** Nạp tiền, trả về số dư mới. Số tiền phải > 0, nếu không ném IllegalArgumentException. */
    public long deposit(long amount) {
        return 0; // TODO
    }

    public String getType() {
        return "?"; // TODO
    }

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", getId(), getType(), "?", getBalance()); // TODO: chủ tài khoản
    }
}
