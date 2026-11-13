// Account.java
public abstract class Account {
    private static int nextId = 1;

    protected final int id;
    protected final String owner;
    protected long balance;

    protected Account(String owner, long initialBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner name must not be blank");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative");
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
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        balance += amount;
        return balance;
    }

    public long withdraw(long amount) throws InsufficientFundsException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (!canWithdraw(amount)) {
            throw new InsufficientFundsException(this, amount);
        }
        balance -= amount;
        return balance;
    }

    /** Each account type has its own withdrawal rule. */
    protected abstract boolean canWithdraw(long amount);

    /** Month end: savings earn interest, checking pays a fee. */
    public abstract void endOfMonth();

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", id, getType(), owner, balance);
    }
}
