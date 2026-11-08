import java.util.ArrayList;
import java.util.List;

/** Quản lý danh sách tài khoản. */
public class Bank {
    private final String name;
    private final List<Account> accounts = new ArrayList<>();

    public Bank(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** type: "S" = tiết kiệm (SAVINGS), "C" = thanh toán (CHECKING). Loại khác ⇒ IllegalArgumentException. */
    public Account open(String type, String owner, long initialBalance) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Không có tài khoản id ⇒ IllegalArgumentException. */
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
