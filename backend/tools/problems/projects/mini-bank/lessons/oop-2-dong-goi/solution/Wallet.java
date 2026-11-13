public class Wallet {
    private final String owner;
    private long balance;

    public Wallet(String owner) {
        this.owner = owner;
    }

    public void deposit(long amount) {
        requirePositive(amount);
        balance += amount;
    }

    public void pay(long amount) {
        requirePositive(amount);
        if (amount > balance) {
            throw new IllegalArgumentException("Không đủ tiền");
        }
        balance -= amount;
    }

    public long getBalance() {
        return balance;
    }

    public String getOwner() {
        return owner;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền phải dương");
        }
    }
}
