import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        List<Shape> shapes = new ArrayList<>();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            // TODO: CIRCLE r / RECT w h / SQUARE a -> add to shapes
        }
        // TODO: print describe() of each, then "Largest: " + describe() of the largest; no shapes -> "No shapes"
    }
}
