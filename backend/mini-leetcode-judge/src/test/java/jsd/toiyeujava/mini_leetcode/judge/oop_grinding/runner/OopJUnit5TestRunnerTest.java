package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OopJUnit5TestRunnerTest {

    private OopJUnit5TestRunner runner;

    @BeforeEach
    void setUp() {
        runner = new OopJUnit5TestRunner();
    }

    private void compileJavaSource(Path outputDir, String fileName, String sourceCode) throws IOException {
        Path sourcePath = outputDir.resolve(fileName);
        if (sourcePath.getParent() != null) {
            Files.createDirectories(sourcePath.getParent());
        }
        Files.writeString(sourcePath, sourceCode);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("System JavaCompiler should be available").isNotNull();

        String classPath = System.getProperty("java.class.path") + System.getProperty("path.separator") + outputDir.toString();

        int result = compiler.run(null, null, null,
                "-d", outputDir.toString(),
                "-cp", classPath,
                sourcePath.toString());
        assertThat(result).as("Compilation of " + fileName + " should succeed").isZero();
    }

    @Test
    @DisplayName("Test 1: Perfect Circle submission passes all 5 tests with 100.0 score")
    void runTests_CompliantCircle_PassesAllTests(@TempDir Path classesDir) throws Exception {
        String shapeCode = """
                package jsd.fixture;
                public abstract class Shape {
                    private String color;
                    public Shape(String color) { this.color = color; }
                    public String getColor() { return color; }
                    public abstract double area();
                    public abstract double perimeter();
                }
                """;

        String circleCode = """
                package jsd.fixture;
                public class Circle extends Shape {
                    private double radius;
                    public Circle() { super("red"); this.radius = 1.0; }
                    public Circle(double radius) { super("red"); this.radius = radius; }
                    public Circle(double radius, String color) { super(color); this.radius = radius; }
                    public double getRadius() { return radius; }
                    @Override
                    public double area() { return Math.PI * radius * radius; }
                    @Override
                    public double perimeter() { return 2 * Math.PI * radius; }
                }
                """;

        String testCode = """
                package jsd.fixture;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class SampleCircleTest {
                    @Test
                    void testDefaultConstructor() {
                        Circle c = new Circle();
                        assertEquals(1.0, c.getRadius(), 1e-6);
                    }

                    @Test
                    void testArea() {
                        Circle c = new Circle(2.0);
                        assertEquals(Math.PI * 4.0, c.area(), 1e-6);
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/fixture/Shape.java", shapeCode);
        compileJavaSource(classesDir, "jsd/fixture/Circle.java", circleCode);
        compileJavaSource(classesDir, "jsd/fixture/SampleCircleTest.java", testCode);

        ExecutionReport.UnitTestPhase phase = runner.runTests(
                classesDir,
                classesDir,
                List.of("jsd.fixture.SampleCircleTest"),
                2,
                0.60
        );

        assertThat(phase.getPassedCount()).isEqualTo(2);
        assertThat(phase.getTotalCount()).isEqualTo(2);
        assertThat(phase.getScore()).isEqualTo(100.0);
        assertThat(phase.getWeightedScore()).isEqualTo(60.0);
        assertThat(phase.getFailures()).isEmpty();
    }

    @Test
    @DisplayName("Test 2: Logic bug in method causes test failure with granular failure report")
    void runTests_BrokenCalculation_RecordsTestFailure(@TempDir Path classesDir) throws Exception {
        String circleCode = """
                package jsd.fixture;
                public class BrokenCircle {
                    public double area() { return -1.0; } // Broken!
                }
                """;

        String testCode = """
                package jsd.fixture;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class BrokenCircleTest {
                    @Test
                    void testAreaPositive() {
                        BrokenCircle c = new BrokenCircle();
                        assertEquals(10.0, c.area(), 1e-6, "Area must be 10.0");
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/fixture/BrokenCircle.java", circleCode);
        compileJavaSource(classesDir, "jsd/fixture/BrokenCircleTest.java", testCode);

        ExecutionReport.UnitTestPhase phase = runner.runTests(
                classesDir,
                classesDir,
                List.of("jsd.fixture.BrokenCircleTest"),
                2,
                0.60
        );

        assertThat(phase.getPassedCount()).isEqualTo(0);
        assertThat(phase.getTotalCount()).isEqualTo(1);
        assertThat(phase.getScore()).isEqualTo(0.0);
        assertThat(phase.getFailures()).hasSize(1);
        ExecutionReport.UnitTestFailure failure = phase.getFailures().get(0);
        assertThat(failure.getTestClass()).isEqualTo("BrokenCircleTest");
        assertThat(failure.getTestMethod()).isEqualTo("testAreaPositive");
        assertThat(failure.getError()).contains("AssertionFailedError");
    }

    @Test
    @DisplayName("Test 3: Empty test class list returns 0/0 and 100.0 score")
    void runTests_EmptyTestClassList_ReturnsEmptyPhase(@TempDir Path classesDir) {
        ExecutionReport.UnitTestPhase phase = runner.runTests(
                classesDir,
                classesDir,
                List.of(),
                2,
                0.60
        );

        assertThat(phase.getPassedCount()).isEqualTo(0);
        assertThat(phase.getTotalCount()).isEqualTo(0);
        assertThat(phase.getScore()).isEqualTo(100.0);
        assertThat(phase.getFailures()).isEmpty();
    }
}
