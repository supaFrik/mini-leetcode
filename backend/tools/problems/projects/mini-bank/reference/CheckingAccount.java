// CheckingAccount.java
public class CheckingAccount extends Account {
    public static final long OVERDRAFT_LIMIT = 500_000;
    public static final long MONTHLY_FEE = 10_000;

    public CheckingAccount(String owner, long initialBalance) {
        super(owner, initialBalance);
    }

    @Override
    protected boolean canWithdraw(long amount) {
        return balance - amount >= -OVERDRAFT_LIMIT;
    }

    @Override
    public void endOfMonth() {
        balance -= MONTHLY_FEE;
    }

    @Override
    public String getType() {
        return "CHECKING";
    }
}
