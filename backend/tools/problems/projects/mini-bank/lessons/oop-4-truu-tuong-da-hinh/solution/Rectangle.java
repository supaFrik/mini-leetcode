public class Rectangle extends Shape {
    private final double w;
    private final double h;

    public Rectangle(double w, double h) {
        this.w = w;
        this.h = h;
    }

    @Override
    public double area() {
        return w * h;
    }

    @Override
    public String name() {
        return "Rectangle";
    }
}
