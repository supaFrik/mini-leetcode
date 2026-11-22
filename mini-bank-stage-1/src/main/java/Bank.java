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

    public Account open(String type, String owner, long initialBalance) {
        if (!"S".equalsIgnoreCase(type) && !"C".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Unknown account type: " + type + " (use S or C)");
        }
        Account account = new Account(type, owner, initialBalance);
        accounts.add(account);
        return account;
    }

    public Account find(int id) {
        for (Account account : accounts) {
            if (account.getId() == id) {
                return account;
            }
        }
        throw new IllegalArgumentException("Account not found: #" + id);
    }

    public long totalBalance() {
        long total = 0;
        for (Account account : accounts) {
            total += account.getBalance();
        }
        return total;
    }

    public List<Account> getAccounts() {
        return accounts;
    }
}
