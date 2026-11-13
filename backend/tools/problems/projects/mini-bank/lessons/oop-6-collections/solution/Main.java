import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Library library = new Library();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+", 3);
            switch (p[0]) {
                case "ADD" -> {
                    String title = p.length > 2 ? p[2].trim().replaceAll("\\s+", " ") : "";
                    System.out.println(library.add(new Book(p[1], title)) ? "Added " + p[1] : "Duplicate id " + p[1]);
                }
                case "FIND" -> {
                    Book book = library.find(p[1]);
                    System.out.println(book != null ? book.toString() : "Not found: " + p[1]);
                }
                case "REMOVE" -> System.out.println(library.remove(p[1]) ? "Removed " + p[1] : "Not found: " + p[1]);
                case "LIST" -> {
                    List<Book> all = library.all();
                    if (all.isEmpty()) {
                        System.out.println("(empty)");
                    }
                    all.forEach(System.out::println);
                }
                case "COUNT" -> System.out.println("Books: " + library.size());
                default -> System.err.println("Unknown command: " + p[0]);
            }
        }
    }
}
