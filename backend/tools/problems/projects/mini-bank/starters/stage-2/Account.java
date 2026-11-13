/** Stage 2: Account is abstract; subclasses decide withdrawals and month-end. Drop the type field. */
public abstract class Account {
    // TODO: id, owner, balance (not public)

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

    /** Withdraws if canWithdraw(amount); otherwise fails and keeps the balance. Returns the new balance. */
    public long withdraw(long amount) {
        return 0; // TODO
    }

    protected abstract boolean canWithdraw(long amount);

    public abstract void endOfMonth();

    public abstract String getType();

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", getId(), getType(), "?", getBalance()); // TODO: owner
    }
}
