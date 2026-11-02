package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.fixtures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(value = 2, unit = TimeUnit.SECONDS)
public class CircleTest {

    private static final double EPSILON = 1e-6;

    @Test
    @DisplayName("Test default constructor")
    void testDefaultConstructor() {
        Circle c = new Circle();
        assertEquals(1.0, c.getRadius(), EPSILON);
        assertEquals("red", c.getColor());
    }

    @Test
    @DisplayName("Test parameterized constructor")
    void testParameterizedConstructor() {
        Circle c = new Circle(3.5, "blue");
        assertEquals(3.5, c.getRadius(), EPSILON);
        assertEquals("blue", c.getColor());
    }

    @Test
    @DisplayName("Test area calculation")
    void testArea() {
        Circle c = new Circle(2.0);
        assertEquals(Math.PI * 4.0, c.area(), EPSILON);
    }

    @Test
    @DisplayName("Test perimeter calculation")
    void testPerimeter() {
        Circle c = new Circle(2.0);
        assertEquals(4.0 * Math.PI, c.perimeter(), EPSILON);
    }

    @Test
    @DisplayName("Test perimeter with zero radius")
    void testPerimeterZeroRadius() {
        Circle c = new Circle(0.0);
        assertEquals(0.0, c.perimeter(), EPSILON);
    }
}
