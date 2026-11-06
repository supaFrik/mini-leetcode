// SavingsAccount.java
public class SavingsAccount extends Account {
    public static final long MIN_BALANCE = 50_000;
    public static final double MONTHLY_RATE = 0.005;

    public SavingsAccount(String owner, long initialBalance) {
        super(owner, initialBalance);
    }

    @Override
    protected boolean canWithdraw(long amount) {
        return balance - amount >= MIN_BALANCE;
    }

    @Override
    public void endOfMonth() {
        balance += Math.round(balance * MONTHLY_RATE);
    }

    @Override
    public String getType() {
        return "SAVINGS";
    }
}
