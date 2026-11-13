/** Stage 1: the type ("SAVINGS" / "CHECKING") is a field. No public fields. */
public class Account {
    // TODO: id (auto-increment from 1), type, owner, balance

    public Account(String type, String owner, long initialBalance) {
        // TODO
    }

    public int getId() {
        return 0; // TODO
    }

    public long getBalance() {
        return 0; // TODO
    }

    /** Returns the new balance. amount <= 0 throws IllegalArgumentException. */
    public long deposit(long amount) {
        return 0; // TODO
    }

    public String getType() {
        return "?"; // TODO
    }

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", getId(), getType(), "?", getBalance()); // TODO: owner
    }
}
