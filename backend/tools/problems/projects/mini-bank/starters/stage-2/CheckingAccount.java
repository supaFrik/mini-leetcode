/** Thanh toán: được thấu chi tới -500.000; cuối tháng trừ phí 10.000. */
public class CheckingAccount extends Account {

    public CheckingAccount(String owner, long initialBalance) {
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
        return "CHECKING";
    }
}
