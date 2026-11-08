/** Tiết kiệm: sau khi rút còn ít nhất 50.000; cuối tháng cộng lãi 0,5% (làm tròn). */
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
