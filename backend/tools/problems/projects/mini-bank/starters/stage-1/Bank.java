import java.util.ArrayList;
import java.util.List;

public class Bank {
    private final String name;
    private final List<Account> accounts = new ArrayList<>();

    public Bank(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** "S" = SAVINGS, "C" = CHECKING; anything else throws IllegalArgumentException. */
    public Account open(String type, String owner, long initialBalance) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Unknown id throws IllegalArgumentException. */
    public Account find(int id) {
        throw new UnsupportedOperationException("TODO");
    }

    public long totalBalance() {
        return 0; // TODO
    }

    public List<Account> getAccounts() {
        return accounts;
    }
}
