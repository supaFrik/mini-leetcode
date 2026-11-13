import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        List<Shape> shapes = new ArrayList<>();
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "CIRCLE" -> shapes.add(new Circle(Double.parseDouble(p[1])));
                case "RECT" -> shapes.add(new Rectangle(Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                case "SQUARE" -> shapes.add(new Square(Double.parseDouble(p[1])));
                default -> System.err.println("Unknown shape: " + p[0]);
            }
        }
        if (shapes.isEmpty()) {
            System.out.println("No shapes");
            return;
        }
        Shape largest = shapes.get(0);
        for (Shape s : shapes) {
            System.out.println(s.describe());
            if (s.area() > largest.area()) {
                largest = s;
            }
        }
        System.out.println("Largest: " + largest.describe());
    }
}
