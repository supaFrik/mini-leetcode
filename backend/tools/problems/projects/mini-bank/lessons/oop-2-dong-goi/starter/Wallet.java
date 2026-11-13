/** Bài 2 — ví điện tử: số dư chỉ đổi qua deposit / pay. Đề: lesson.md. */
public class Wallet {
    // TODO 1: field private final String owner và private long balance. KHÔNG field public, KHÔNG setter.

    public Wallet(String owner) {
        // TODO 2
    }

    public void deposit(long amount) {
        // TODO 3: amount <= 0 => throw new IllegalArgumentException("Số tiền phải dương")
    }

    public void pay(long amount) {
        // TODO 4: amount <= 0 => "Số tiền phải dương"; amount > balance => "Không đủ tiền"
    }

    public long getBalance() {
        return 0; // TODO 5
    }

    public String getOwner() {
        return ""; // TODO 6
    }
}
