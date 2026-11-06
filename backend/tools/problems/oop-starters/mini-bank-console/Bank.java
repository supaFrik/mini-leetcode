// Bank.java
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

    /** type: S = tiết kiệm, C = thanh toán. */
    public Account open(String type, String owner, long initialBalance) {
        Account account = switch (type.toUpperCase()) {
            case "S" -> new SavingsAccount(owner, initialBalance);
            case "C" -> new CheckingAccount(owner, initialBalance);
            default -> throw new IllegalArgumentException("Loại tài khoản không hợp lệ: " + type + " (dùng S hoặc C)");
        };
        accounts.add(account);
        return account;
    }

    public Account find(int id) {
        for (Account account : accounts) {
            if (account.getId() == id) {
                return account;
            }
        }
        throw new IllegalArgumentException("Không tìm thấy tài khoản #" + id);
    }

    public void transfer(int fromId, int toId, long amount) throws InsufficientFundsException {
        Account from = find(fromId);
        Account to = find(toId);
        from.withdraw(amount);
        to.deposit(amount);
    }

    public void endOfMonth() {
        for (Account account : accounts) {
            account.endOfMonth();
        }
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
