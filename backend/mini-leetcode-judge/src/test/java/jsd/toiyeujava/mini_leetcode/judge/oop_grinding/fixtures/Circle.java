package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.fixtures;

public class Circle extends Shape {
    private double radius;

    public Circle() {
        super("red");
        this.radius = 1.0;
    }

    public Circle(double radius) {
        super("red");
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        this.radius = radius;
    }

    public Circle(double radius, String color) {
        super(color);
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public double perimeter() {
        return 2 * Math.PI * radius;
    }
}
