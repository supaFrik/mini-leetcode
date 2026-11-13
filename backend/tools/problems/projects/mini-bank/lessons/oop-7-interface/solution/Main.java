import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class Main {
    public static void main(String[] args) {
        Map<String, Command> commands = new TreeMap<>();
        for (Command c : List.of(new EchoCommand(), new AddCommand(), new UpperCommand())) {
            commands.put(c.name(), c);
        }

        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+", 2);
            String name = p[0];
            String rest = p.length > 1 ? p[1] : "";
            if (name.equals("HELP")) {
                System.out.println("Lệnh: " + String.join(", ", commands.keySet()));
                continue;
            }
            Command command = commands.get(name);
            if (command == null) {
                System.out.println("Lệnh không hỗ trợ: " + name);
                continue;
            }
            try {
                System.out.println(command.run(rest));
            } catch (IllegalArgumentException e) {
                System.out.println("Lỗi: " + e.getMessage());
            }
        }
    }
}
