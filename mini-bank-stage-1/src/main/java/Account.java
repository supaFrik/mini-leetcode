public class Account {
    private static int nextId = 1;
    private final int id;
    private final String type;
    private final String owner;
    private long balance;

    public Account(String type, String owner, long initialBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner name must not be blank");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative");
        }
        this.id = nextId++;
        this.type = type.equalsIgnoreCase("S") ? "SAVINGS" : type.equalsIgnoreCase("C") ? "CHECKING" : type;
        this.owner = owner;
        this.balance = initialBalance;
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
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

    @Override
    public String toString() {
        return String.format("#%d %-12s %-10s %,15d", id, type, owner, balance);
    }
}
