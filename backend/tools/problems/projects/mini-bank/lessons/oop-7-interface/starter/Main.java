import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO: Map<String, Command> commands = new TreeMap<>(); register the three commands by name()
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+", 2);
            String name = p[0];
            String rest = p.length > 1 ? p[1] : "";
            // TODO: HELP -> "Commands: " + names joined by ", "
            //       registered -> print run(rest); IllegalArgumentException -> "Error: " + message
            //       otherwise -> "Unknown command: " + name
        }
    }
}
