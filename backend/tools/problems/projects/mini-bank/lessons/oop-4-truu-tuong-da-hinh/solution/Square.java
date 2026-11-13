public class Square extends Rectangle {
    public Square(double a) {
        super(a, a);
    }

    @Override
    public String name() {
        return "Square";
    }
}
