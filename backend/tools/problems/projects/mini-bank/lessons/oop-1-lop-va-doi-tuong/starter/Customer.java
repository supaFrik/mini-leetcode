/** Bài 1 — một khách hàng của ngân hàng. Đề: lesson.md. */
public class Customer {
    // TODO 1: bộ đếm dùng chung cho cả lớp: private static int nextId = 1;
    // TODO 2: field private cho id và name

    public Customer(String name) {
        // TODO 3: gán name, lấy id từ nextId rồi tăng nextId
    }

    // TODO 4: getId(), getName()

    /** Số khách hàng đã tạo. */
    public static int count() {
        return 0; // TODO 5
    }

    @Override
    public String toString() {
        return ""; // TODO 6: "Customer#<id>: <name>"
    }
}
