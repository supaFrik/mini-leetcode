import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Library library = new Library();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+", 3);   // command, id, title
            // TODO: handle ADD / FIND / REMOVE / LIST / COUNT using Library methods only
        }
    }
}
