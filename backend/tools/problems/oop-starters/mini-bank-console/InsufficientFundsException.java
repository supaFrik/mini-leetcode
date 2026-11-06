// InsufficientFundsException.java
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(Account account, long amount) {
        super(String.format("Tài khoản #%d (%s) không đủ để rút %,d — số dư hiện tại %,d",
                account.getId(), account.getType(), amount, account.getBalance()));
    }
}
