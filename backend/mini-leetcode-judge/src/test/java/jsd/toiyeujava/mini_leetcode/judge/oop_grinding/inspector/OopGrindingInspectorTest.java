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

    @Test
    @DisplayName("Test 8: Wildcard encapsulation passes with private, protected, and static final fields")
    void inspect_WildcardEncapsulation_PassesWithPrivateProtectedAndStaticFinal(@TempDir Path classesDir) throws Exception {
        String accountCode = """
                package jsd.test.bank;
                public class Account {
                    public static final double MAX_TRANSFER = 50000.0;
                    protected String accountType;
                    private double balance;
                    public Account(String type, double balance) {
                        this.accountType = type;
                        this.balance = balance;
                    }
                    public double getBalance() { return balance; }
                }
                """;

        String bankCode = """
                package jsd.test.bank;
                import java.util.ArrayList;
                import java.util.List;
                public class Bank {
                    private List<Account> accounts = new ArrayList<>();
                    public void addAccount(Account account) { accounts.add(account); }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/bank/Account.java", accountCode);
        compileJavaSource(classesDir, "jsd/test/bank/Bank.java", bankCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("WILDCARD_ENCAPSULATION")
                        .targetClassName("*")
                        .enforceEncapsulation(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Test 9: Wildcard encapsulation fails on public mutable and package-private fields")
    void inspect_WildcardEncapsulation_FailsOnPublicOrPackagePrivateField(@TempDir Path classesDir) throws Exception {
        String accountCode = """
                package jsd.test.bank;
                public class Account {
                    double balance; // package-private mutable violation!
                    public Account(double balance) { this.balance = balance; }
                }
                """;

        String bankCode = """
                package jsd.test.bank;
                public class Bank {
                    public String bankName; // public non-final violation!
                }
                """;

        compileJavaSource(classesDir, "jsd/test/bank/Account.java", accountCode);
        compileJavaSource(classesDir, "jsd/test/bank/Bank.java", bankCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("WILDCARD_ENCAPSULATION")
                        .targetClassName("*")
                        .enforceEncapsulation(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getScore()).isEqualTo(0.0);
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.getMessage()).contains("Account");
        assertThat(result.getMessage()).contains("balance");
        assertThat(result.getMessage()).contains("Bank");
        assertThat(result.getMessage()).contains("bankName");
    }

    @Test
    @DisplayName("Test 10: Wildcard polymorphism passes when interface or abstract class exists")
    void inspect_WildcardPolymorphism_PassesWhenInterfaceOrAbstractClassExists(@TempDir Path classesDir) throws Exception {
        String flyableCode = """
                package jsd.test.animals;
                public interface Flyable {
                    void fly();
                }
                """;

        String birdCode = """
                package jsd.test.animals;
                public class Bird implements Flyable {
                    @Override
                    public void fly() {}
                }
                """;

        compileJavaSource(classesDir, "jsd/test/animals/Flyable.java", flyableCode);
        compileJavaSource(classesDir, "jsd/test/animals/Bird.java", birdCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("POLYMORPHISM_PRESET")
                        .targetClassName("*")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS_OR_INTERFACE)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Test 11: Wildcard polymorphism fails when all classes are independent concrete classes")
    void inspect_WildcardPolymorphism_FailsWhenAllConcreteIndependentClasses(@TempDir Path classesDir) throws Exception {
        String userCode = """
                package jsd.test.app;
                public class User {
                    private String username;
                    public User(String username) { this.username = username; }
                }
                """;

        String serviceCode = """
                package jsd.test.app;
                public class UserService {
                    public void register(User user) {}
                }
                """;

        compileJavaSource(classesDir, "jsd/test/app/User.java", userCode);
        compileJavaSource(classesDir, "jsd/test/app/UserService.java", serviceCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("POLYMORPHISM_PRESET")
                        .targetClassName("*")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS_OR_INTERFACE)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage()).contains("Polymorphism violation");
    }

    @Test
    @DisplayName("Test 12: Target class ABSTRACT_CLASS_OR_INTERFACE passes for interface and abstract class, fails for concrete class")
    void inspect_TargetClass_AbstractClassOrInterface_PassesForBothAndFailsForConcrete(@TempDir Path classesDir) throws Exception {
        String shapeCode = "package jsd.test.poly; public abstract class Shape {}";
        String drawableCode = "package jsd.test.poly; public interface Drawable {}";
        String circleCode = "package jsd.test.poly; public class Circle {}";

        compileJavaSource(classesDir, "jsd/test/poly/Shape.java", shapeCode);
        compileJavaSource(classesDir, "jsd/test/poly/Drawable.java", drawableCode);
        compileJavaSource(classesDir, "jsd/test/poly/Circle.java", circleCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("CHECK_SHAPE")
                        .targetClassName("jsd.test.poly.Shape")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS_OR_INTERFACE)
                        .weight(1.0)
                        .build(),
                OopConstraintRule.builder()
                        .ruleName("CHECK_DRAWABLE")
                        .targetClassName("jsd.test.poly.Drawable")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS_OR_INTERFACE)
                        .weight(1.0)
                        .build(),
                OopConstraintRule.builder()
                        .ruleName("CHECK_CIRCLE")
                        .targetClassName("jsd.test.poly.Circle")
                        .expectedType(ExpectedClassType.ABSTRACT_CLASS_OR_INTERFACE)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.getRules()).hasSize(3);
        assertThat(report.getRules().get(0).isPassed()).isTrue();
        assertThat(report.getRules().get(1).isPassed()).isTrue();
        assertThat(report.getRules().get(2).isPassed()).isFalse();
        assertThat(report.getRules().get(2).getMessage()).contains("Expected ABSTRACT_CLASS_OR_INTERFACE, but found CLASS");
    }

    @Test
    @DisplayName("Test 13: Anti-monolithic preset passes with multiple classes and main delegating to domain class")
    void inspect_AntiMonolithicPreset_PassesWithMultipleClassesAndMainDelegation(@TempDir Path classesDir) throws Exception {
        String accountCode = """
                package jsd.test.bank;
                public class Account {
                    private double balance;
                    public void deposit(double a) { balance += a; }
                    public double getBalance() { return balance; }
                }
                """;

        String mainCode = """
                package jsd.test.bank;
                public class Main {
                    public static void main(String[] args) {
                        Account acc = new Account();
                        acc.deposit(100.0);
                        System.out.println(acc.getBalance());
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/bank/Account.java", accountCode);
        compileJavaSource(classesDir, "jsd/test/bank/Main.java", mainCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ANTI_MONOLITHIC")
                        .targetClassName("*")
                        .minClassCount(2)
                        .disallowMonolithicMain(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Test 14: Anti-monolithic preset fails when only one monolithic class is submitted")
    void inspect_AntiMonolithicPreset_FailsWhenOnlyOneClassSubmitted(@TempDir Path classesDir) throws Exception {
        String mainCode = """
                package jsd.test.monolith;
                public class Main {
                    public static void main(String[] args) {
                        double[] balances = new double[10];
                        balances[0] = 100.0;
                        System.out.println("All banking logic inside single main file");
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/monolith/Main.java", mainCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ANTI_MONOLITHIC")
                        .targetClassName("*")
                        .minClassCount(2)
                        .disallowMonolithicMain(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.getMessage()).contains("Anti-monolithic violation");
        assertThat(result.getMessage()).contains("expected at least 2 classes");
    }

    @Test
    @DisplayName("Test 15: Anti-monolithic preset fails when main does not reference or delegate to declared domain class")
    void inspect_AntiMonolithicPreset_FailsWhenMainDoesNotDelegateToDomainClasses(@TempDir Path classesDir) throws Exception {
        String unusedDomainCode = """
                package jsd.test.monolith;
                public class UnusedDomain {
                    private int id;
                }
                """;

        String mainCode = """
                package jsd.test.monolith;
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Monolithic main calculating everything on its own without using UnusedDomain");
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/monolith/UnusedDomain.java", unusedDomainCode);
        compileJavaSource(classesDir, "jsd/test/monolith/Main.java", mainCode);

        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("ANTI_MONOLITHIC")
                        .targetClassName("*")
                        .minClassCount(2)
                        .disallowMonolithicMain(true)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        OopRuleResult result = report.getRules().get(0);
        assertThat(result.getMessage()).contains("Anti-monolithic violation: main method contains entire program logic without delegating to domain classes");
    }

    @Test
    @DisplayName("Test 16: Anonymous inner classes and harness classes are excluded from user class counting")
    void inspect_AnonymousInnerClassesAndHarness_ExcludedFromUserClassCount(@TempDir Path classesDir) throws Exception {
        String mainCode = """
                package jsd.test.anon;
                public class Main {
                    public static void main(String[] args) {
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                System.out.println("Inside anonymous inner class");
                            }
                        };
                        r.run();
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/anon/Main.java", mainCode);

        // Expect at least 2 classes. Even though javac generates Main$1.class, anonymous class must NOT be counted as domain class!
        List<OopConstraintRule> rules = List.of(
                OopConstraintRule.builder()
                        .ruleName("COUNT_CHECK")
                        .targetClassName("*")
                        .minClassCount(2)
                        .weight(1.0)
                        .build()
        );

        OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage()).contains("expected at least 2 classes, but found 1");
    }

    @Test
    @DisplayName("Test 17: Full three-preset JSON configuration evaluates end-to-end")
    void inspect_FullThreePresetJsonConfig_EvaluatesCorrectly(@TempDir Path classesDir) throws Exception {
        String shapeCode = """
                package jsd.test.complete;
                public interface Shape {
                    double area();
                }
                """;

        String circleCode = """
                package jsd.test.complete;
                public class Circle implements Shape {
                    private double radius;
                    public Circle(double radius) { this.radius = radius; }
                    @Override
                    public double area() { return Math.PI * radius * radius; }
                }
                """;

        String mainCode = """
                package jsd.test.complete;
                public class Main {
                    public static void main(String[] args) {
                        Shape s = new Circle(5.0);
                        System.out.println(s.area());
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/complete/Shape.java", shapeCode);
        compileJavaSource(classesDir, "jsd/test/complete/Circle.java", circleCode);
        compileJavaSource(classesDir, "jsd/test/complete/Main.java", mainCode);

        String presetsJson = """
                [
                  {
                    "ruleId": "preset-encapsulation",
                    "ruleName": "Encapsulation: fields must be private or protected",
                    "targetClassName": "*",
                    "enforceEncapsulation": true,
                    "weight": 1.0
                  },
                  {
                    "ruleId": "preset-polymorphism",
                    "ruleName": "Polymorphism: requires interface or abstract class",
                    "targetClassName": "*",
                    "expectedType": "ABSTRACT_CLASS_OR_INTERFACE",
                    "weight": 1.0
                  },
                  {
                    "ruleId": "preset-anti-monolithic",
                    "ruleName": "Anti-Monolithic: multi-class design",
                    "targetClassName": "*",
                    "minClassCount": 2,
                    "disallowMonolithicMain": true,
                    "weight": 1.0
                  }
                ]
                """;

        OopInspectionReport report = inspector.inspect(classesDir, presetsJson, 0.40);

        assertThat(report.isPassed()).isTrue();
        assertThat(report.getScore()).isEqualTo(100.0);
        assertThat(report.getRules()).hasSize(3);
        assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
    }
}
