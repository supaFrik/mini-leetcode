/** Keeps at least 50,000 after a withdrawal; month-end interest 0.5% (rounded). */
public class SavingsAccount extends Account {

    public SavingsAccount(String owner, long initialBalance) {
        super(owner, initialBalance);
    }

    @Override
    protected boolean canWithdraw(long amount) {
        return false; // TODO
    }

    @Override
    public void endOfMonth() {
        // TODO
    }

    @Override
    public String getType() {
        return "SAVINGS";
    }
}
