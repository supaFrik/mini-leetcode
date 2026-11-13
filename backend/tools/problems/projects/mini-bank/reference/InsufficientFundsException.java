// InsufficientFundsException.java
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(Account account, long amount) {
        super(String.format("Account #%d (%s) cannot withdraw %,d; balance is %,d",
                account.getId(), account.getType(), amount, account.getBalance()));
    }
}
