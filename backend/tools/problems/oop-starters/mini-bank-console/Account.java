// Account.java
public abstract class Account {
    private static int nextId = 1;

    protected final int id;
    protected final String owner;
    protected long balance;

    protected Account(String owner, long initialBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Tên chủ tài khoản không được để trống");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Số dư ban đầu không được âm");
        }
        this.id = nextId++;
        this.owner = owner;
        this.balance = initialBalance;
    }

    public int getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public long getBalance() {
        return balance;
    }

    public long deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
        balance += amount;
        return balance;
    }

    public long withdraw(long amount) throws InsufficientFundsException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }
        if (!canWithdraw(amount)) {
            throw new InsufficientFundsException(this, amount);
        }
        balance -= amount;
        return balance;
    }

    /** Mỗi loại tài khoản có quy tắc rút tiền riêng. */
    protected abstract boolean canWithdraw(long amount);

    /** Cuối tháng: tiết kiệm được cộng lãi, thanh toán bị trừ phí. */
    public abstract void endOfMonth();

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", id, getType(), owner, balance);
    }
}
