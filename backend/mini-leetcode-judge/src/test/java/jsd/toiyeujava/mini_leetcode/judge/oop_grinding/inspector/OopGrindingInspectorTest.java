package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
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
import static org.assertj.core.api.Assertions.assertThatNoException;

class OopGrindingInspectorTest {

    private OopGrindingInspector inspector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        inspector = new OopGrindingInspector(objectMapper);
    }

    private void compileJavaSource(Path outputDir, String fileName, String sourceCode) throws IOException {
        Path sourcePath = outputDir.resolve(fileName);
        if (sourcePath.getParent() != null) {
            Files.createDirectories(sourcePath.getParent());
        }
        Files.writeString(sourcePath, sourceCode);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("System JavaCompiler should be available in JDK 17").isNotNull();

        int result = compiler.run(null, null, null,
                "-d", outputDir.toString(),
                "-cp", outputDir.toString(),
                sourcePath.toString());
        assertThat(result).as("Compilation of " + fileName + " should succeed").isZero();
    }

    @Test
    @DisplayName("Test 1: Fully compliant Shape and Circle classes pass all rules with 100.0 score")
    void inspect_CompliantShapeAndCircle_PassesAllRules(@TempDir Path classesDir) throws Exception {
        String shapeCode = """
                package jsd.test.shape;
                public abstract class Shape {
                    private String color;
                    public Shape() { this.color = "unknown"; }
                    public Shape(String color) { this.color = color; }
                    public String getColor() { return color; }
                    public void setColor(String color) { this.color = color; }
                    public abstract double area();
                    public abstract double perimeter();
                }
                """;

        String circleCode = """
                package jsd.test.shape;
                public class Circle extends Shape {
                    private double radius;
                    public Circle() { super("red"); this.radius = 1.0; }
                    public Circle(double radius) { super("red"); this.radius = radius; }
                    public double getRadius() { return radius; }
                    public void setRadius(double radius) { this.radius = radius; }
                    @Override
                    public double area() { return Math.PI * radius * radius; }
                    @Override
                    public double perimeter() { return 2 * Math.PI * radius; }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/shape/Shape.java", shapeCode);
        compileJavaSource(classesDir, "jsd/test/shape/Circle.java", circleCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ABSTRACT_CLASS_SHAPE")
                        .targetClassName("jsd.test.shape.Shape")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS)
                        .requiredModifiers(List.of("public", "abstract"))
                        .methods(List.of(
                                OopMethodConstraint.builder().name("area").returnType("double").requiredModifiers(List.of("public", "abstract")).build(),
                                OopMethodConstraint.builder().name("perimeter").returnType("double").requiredModifiers(List.of("public", "abstract")).build()
                        ))
                        .weight(1.0)
                        .build(),
                OopConstraintRule.builder()
                        .ruleName("ENCAPSULATION_CIRCLE")
                        .targetClassName("jsd.test.shape.Circle")
                        .expectedType(ExpectedClassType.CLASS)
                        .expectedSuperclass("Shape")
                        .enforceEncapsulation(true)
                        .constructors(List.of(
                                OopConstructorConstraint.builder().parameterTypes(List.of("double")).requiredModifiers(List.of("public")).build()
                        ))
                        .methods(List.of(
                                OopMethodConstraint.builder().name("area").returnType("double").requiredModifiers(List.of("public")).build(),
                                OopMethodConstraint.builder().name("perimeter").returnType("double").requiredModifiers(List.of("public")).build()
                        ))
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
        assertThat(report.getWeightedScore()).isEqualTo(40.0);
        assertThat(report.getRules()).hasSize(2);
        assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
    }

    @Test
    @DisplayName("Test 2: Encapsulation violation (public field) fails encapsulation rule")
    void inspect_EncapsulationViolation_FailsRuleWithDetails(@TempDir Path classesDir) throws Exception {
        String circleCode = """
                package jsd.test.shape;
                public class Circle {
                    public double radius; // Encapsulation violation!
                    public Circle(double radius) { this.radius = radius; }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/shape/Circle.java", circleCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ENCAPSULATION_CIRCLE")
                        .targetClassName("jsd.test.shape.Circle")
                        .enforceEncapsulation(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getScore()).isEqualTo(0.0);
        assertThat(report.getRules()).hasSize(1);
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("radius");
        assertThat(result.getMessage()).contains("not private");
    }

    @Test
    @DisplayName("Test 3: Missing abstract modifier fails ABSTRACT_CLASS check")
    void inspect_MissingAbstractModifier_FailsClassTypeRule(@TempDir Path classesDir) throws Exception {
        String shapeCode = """
                package jsd.test.shape;
                public class Shape { // Missing abstract keyword!
                    public double area() { return 0.0; }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/shape/Shape.java", shapeCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ABSTRACT_CLASS_SHAPE")
                        .targetClassName("jsd.test.shape.Shape")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS)
                        .requiredModifiers(List.of("public", "abstract"))
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getScore()).isEqualTo(0.0);
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("Expected ABSTRACT_CLASS");
    }

    @Test
    @DisplayName("Test 4: Missing parameterized constructor fails constructor rule")
    void inspect_MissingConstructor_FailsConstructorConstraint(@TempDir Path classesDir) throws Exception {
        String circleCode = """
                package jsd.test.shape;
                public class Circle {
                    private double radius;
                    // Only default constructor
                    public Circle() { this.radius = 1.0; }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/shape/Circle.java", circleCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("CONSTRUCTOR_CHECK")
                        .targetClassName("jsd.test.shape.Circle")
                        .constructors(List.of(
                                OopConstructorConstraint.builder().parameterTypes(List.of("double")).build()
                        ))
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage()).contains("Constructor Circle(double) not found");
    }

    @Test
    @DisplayName("Test 5: Malicious static initializer is safely suppressed (initialize = false)")
    void inspect_MaliciousStaticInitializer_SafelySuppressedWithoutExecuting(@TempDir Path classesDir) throws Exception {
        String evilCode = """
                package jsd.test.evil;
                public class Evil {
                    static {
                        if (true) {
                            throw new RuntimeException("MALICIOUS_STATIC_INIT_EXECUTED");
                        }
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/evil/Evil.java", evilCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("EVIL_CHECK")
                        .targetClassName("jsd.test.evil.Evil")
                        .expectedType(ExpectedClassType.CLASS)
                        .weight(1.0)
                        .build()
        );

        // Verification: initialize = false must ensure no RuntimeException is thrown during class loading!
        assertThatNoException().isThrownBy(() -> {
            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        });
    }

    @Test
    @DisplayName("Test 6: Missing class returns clean failed rule without throwing ClassNotFoundException")
    void inspect_MissingClass_ReturnsCleanFailedResultWithoutThrowing(@TempDir Path classesDir) {
        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("MISSING_CLASS_RULE")
                        .targetClassName("com.nonexistent.GhostClass")
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getScore()).isEqualTo(0.0);
        assertThat(report.getRules()).hasSize(1);
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("was not found");
    }

    @Test
    @DisplayName("Test 7: Inspect with JSON string rules parses correctly")
    void inspect_JsonStringRules_ParsesAndEvaluatesCorrectly(@TempDir Path classesDir) throws Exception {
        String shapeCode = """
                public abstract class Shape {
                    public abstract double area();
                }
                """;
        compileJavaSource(classesDir, "Shape.java", shapeCode);

        String jsonRules = """
                [
                  {
                    "ruleName": "ABSTRACT_SHAPE",
                    "targetClassName": "Shape",
                    "expectedType": "ABSTRACT_CLASS",
                    "requiredModifiers": ["public", "abstract"],
                    "methods": [
                      {"name": "area", "returnType": "double", "parameterTypes": []}
                    ],
                    "weight": 1.0
                  }
                ]
                """;

        OopInspectionReport report = inspector.inspect(classesDir, jsonRules, 0.40);
        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
    }
}
