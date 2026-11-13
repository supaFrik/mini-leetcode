import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Library {
    private final Map<String, Book> books = new LinkedHashMap<>();

    public boolean add(Book book) {
        return books.putIfAbsent(book.getId(), book) == null;
    }

    public Book find(String id) {
        return books.get(id);
    }

    public boolean remove(String id) {
        return books.remove(id) != null;
    }

    public List<Book> all() {
        return List.copyOf(books.values());
    }

    public int size() {
        return books.size();
    }
}
