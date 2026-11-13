import java.util.List;

/** Bài 6 — thư viện: collection bên trong phải private, không lộ ra ngoài. */
public class Library {
    // TODO 1: private final Map<String, Book> books = new LinkedHashMap<>();

    public boolean add(Book book) {
        return false; // TODO 2: false nếu trùng mã (putIfAbsent)
    }

    public Book find(String id) {
        return null; // TODO 3
    }

    public boolean remove(String id) {
        return false; // TODO 4
    }

    public List<Book> all() {
        return List.of(); // TODO 5: List.copyOf(books.values()) — bản sao không sửa được
    }

    public int size() {
        return 0; // TODO 6
    }
}
