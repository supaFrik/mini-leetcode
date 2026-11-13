/** Stage 3: Account.withdraw declares "throws InsufficientFundsException". */
public class InsufficientFundsException extends Exception {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
