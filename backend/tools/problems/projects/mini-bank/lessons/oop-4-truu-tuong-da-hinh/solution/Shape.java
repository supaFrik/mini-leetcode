import java.util.Locale;

public abstract class Shape {
    public abstract double area();

    public abstract String name();

    public String describe() {
        return String.format(Locale.ROOT, "%s: %.2f", name(), area());
    }
}
