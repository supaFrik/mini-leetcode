import java.util.List;

public class Library {
    // TODO: private final Map<String, Book> books = new LinkedHashMap<>();

    public boolean add(Book book) {
        return false; // TODO: false if the id exists (putIfAbsent)
    }

    public Book find(String id) {
        return null; // TODO
    }

    public boolean remove(String id) {
        return false; // TODO
    }

    public List<Book> all() {
        return List.of(); // TODO: List.copyOf(books.values())
    }

    public int size() {
        return 0; // TODO
    }
}
