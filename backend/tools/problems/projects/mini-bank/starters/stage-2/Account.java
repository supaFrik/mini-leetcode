/**
 * Stage 2: Account trở thành lớp abstract. Mỗi loại tài khoản tự quyết định được rút bao nhiêu và
 * làm gì cuối tháng — bỏ field "loại" của stage 1, dùng getType() của lớp con.
 * (Plugin không ghi đè Account.java bạn đã viết; file này chỉ là gợi ý cấu trúc.)
 */
public abstract class Account {
    // TODO: id tự tăng, chủ tài khoản, số dư (không public)

    protected Account(String owner, long initialBalance) {
        // TODO
    }

    public int getId() {
        return 0; // TODO
    }

    public long getBalance() {
        return 0; // TODO
    }

    public long deposit(long amount) {
        return 0; // TODO
    }

    /** Rút tiền nếu canWithdraw(amount); không được thì báo lỗi và giữ nguyên số dư. Trả về số dư mới. */
    public long withdraw(long amount) {
        return 0; // TODO
    }

    protected abstract boolean canWithdraw(long amount);

    public abstract void endOfMonth();

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", getId(), getType(), "?", getBalance()); // TODO: chủ tài khoản
    }
}
