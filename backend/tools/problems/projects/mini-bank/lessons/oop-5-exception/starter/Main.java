import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int valid = 0;
        int invalid = 0;
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            String name = p[0];
            String raw = p[p.length - 1];
            // TODO: try validate -> "<name>: <age>"; catch InvalidAgeException -> "<name>: ERROR - " + message
        }
        System.out.println("Valid: " + valid + ", invalid: " + invalid);
    }
}
