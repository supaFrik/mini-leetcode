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

    @Test
    @DisplayName("Test 18: REQUIRE_FINAL_FIELDS enforces final instance fields and forbids setters")
    void inspect_RequireFinalFields_EnforcesImmutabilityAndNoSetters(@TempDir Path classesDir) throws Exception {
        String immutableMoney = """
                package jsd.test.immut;
                public class ImmutableMoney {
                    private final long amount;
                    private final String currency;
                    public ImmutableMoney(long amount, String currency) {
                        this.amount = amount;
                        this.currency = currency;
                    }
                    public long getAmount() { return amount; }
                    public String getCurrency() { return currency; }
                }
                """;

        String mutableMoney = """
                package jsd.test.immut;
                public class MutableMoney {
                    private long amount;
                    public MutableMoney(long amount) { this.amount = amount; }
                    public long getAmount() { return amount; }
                }
                """;

        String setterMoney = """
                package jsd.test.immut;
                public class SetterMoney {
                    private final long amount;
                    public SetterMoney(long amount) { this.amount = amount; }
                    public long getAmount() { return amount; }
                    public void setAmount(long amount) { /* violation */ }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/immut/ImmutableMoney.java", immutableMoney);
        compileJavaSource(classesDir, "jsd/test/immut/MutableMoney.java", mutableMoney);
        compileJavaSource(classesDir, "jsd/test/immut/SetterMoney.java", setterMoney);

        // 1. ImmutableMoney should PASS
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.immut.ImmutableMoney")
                .requireFinalFields(true)
                .build();
        OopInspectionReport passReport = inspector.inspect(classesDir, List.of(passRule), 1.0);
        assertThat(passReport.isPassed()).isTrue();

        // 2. MutableMoney should FAIL on non-final field
        OopConstraintRule failFieldRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.immut.MutableMoney")
                .requireFinalFields(true)
                .build();
        OopInspectionReport failFieldReport = inspector.inspect(classesDir, List.of(failFieldRule), 1.0);
        assertThat(failFieldReport.isPassed()).isFalse();
        assertThat(failFieldReport.getRules().get(0).getMessage())
                .contains("Field 'amount' in class MutableMoney must be final (immutability violation)");

        // 3. SetterMoney should FAIL on setter
        OopConstraintRule failSetterRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.immut.SetterMoney")
                .requireFinalFields(true)
                .build();
        OopInspectionReport failSetterReport = inspector.inspect(classesDir, List.of(failSetterRule), 1.0);
        assertThat(failSetterReport.isPassed()).isFalse();
        assertThat(failSetterReport.getRules().get(0).getMessage())
                .contains("Class SetterMoney must not declare setter for immutable field 'amount' ('setAmount' found)");
    }

    @Test
    @DisplayName("Test 19: MAX_METHODS_PER_CLASS restricts maximum method count")
    void inspect_MaxMethodsPerClass_RestrictsCount(@TempDir Path classesDir) throws Exception {
        String code = """
                package jsd.test.limits;
                public class ServiceWithFourMethods {
                    public void m1() {}
                    public void m2() {}
                    public void m3() {}
                    public void m4() {}
                }
                """;
        compileJavaSource(classesDir, "jsd/test/limits/ServiceWithFourMethods.java", code);

        // Pass with limit 5
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.limits.ServiceWithFourMethods")
                .maxMethodsPerClass(5)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // Fail with limit 3
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.limits.ServiceWithFourMethods")
                .maxMethodsPerClass(3)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Class ServiceWithFourMethods has 4 methods, exceeding maximum allowed limit (3)");
    }

    @Test
    @DisplayName("Test 20: MAX_LINES_PER_METHOD enforces maximum source line length per method")
    void inspect_MaxLinesPerMethod_EnforcesLineLimit(@TempDir Path classesDir) throws Exception {
        StringBuilder longMethodBody = new StringBuilder();
        longMethodBody.append("package jsd.test.lines;\n");
        longMethodBody.append("public class LongMethodClass {\n");
        longMethodBody.append("    public int compute() {\n");
        longMethodBody.append("        int x = 0;\n");
        for (int i = 0; i < 25; i++) {
            longMethodBody.append("        x += ").append(i).append(";\n");
        }
        longMethodBody.append("        return x;\n");
        longMethodBody.append("    }\n");
        longMethodBody.append("}\n");

        compileJavaSource(classesDir, "jsd/test/lines/LongMethodClass.java", longMethodBody.toString());

        // Pass with limit 40
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.lines.LongMethodClass")
                .maxLinesPerMethod(40)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // Fail with limit 15
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.lines.LongMethodClass")
                .maxLinesPerMethod(15)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Method 'compute' in class LongMethodClass has")
                .contains("exceeding maximum allowed limit (15)");
    }

    @Test
    @DisplayName("Test 21: REQUIRE_ANNOTATIONS @Override detects missing annotations on overridden methods")
    void inspect_RequireOverrideAnnotation_DetectsMissingOverride(@TempDir Path classesDir) throws Exception {
        String baseCode = """
                package jsd.test.ovr;
                public interface Greeter {
                    String greet();
                }
                """;

        String goodCode = """
                package jsd.test.ovr;
                public class GoodGreeter implements Greeter {
                    @Override
                    public String greet() {
                        return "Hello";
                    }
                }
                """;

        String badCode = """
                package jsd.test.ovr;
                public class BadGreeter implements Greeter {
                    public String greet() {
                        return "Hello";
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/ovr/Greeter.java", baseCode);
        compileJavaSource(classesDir, "jsd/test/ovr/GoodGreeter.java", goodCode);
        compileJavaSource(classesDir, "jsd/test/ovr/BadGreeter.java", badCode);

        // GoodGreeter passes
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.ovr.GoodGreeter")
                .requireOverrideAnnotation(true)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // BadGreeter fails with exact message
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.ovr.BadGreeter")
                .requireOverrideAnnotation(true)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Method 'greet' in class BadGreeter overrides a method from Greeter but is missing @Override annotation");
    }

    @Test
    @DisplayName("Test 22: Anti-Anemic Domain Model requires domain logic beyond getters/setters")
    void inspect_AntiAnemic_DistinguishesRichVsAnemicModel(@TempDir Path classesDir) throws Exception {
        String richCode = """
                package jsd.test.anemic;
                public class RichAccount {
                    private double balance;
                    public RichAccount(double initial) { this.balance = initial; }
                    public double getBalance() { return balance; }
                    public void deposit(double amount) {
                        if (amount > 0) this.balance += amount;
                    }
                }
                """;

        String anemicCode = """
                package jsd.test.anemic;
                public class AnemicAccount {
                    private double balance;
                    public AnemicAccount(double balance) { this.balance = balance; }
                    public double getBalance() { return balance; }
                    public void setBalance(double balance) { this.balance = balance; }
                    @Override
                    public String toString() { return "balance=" + balance; }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/anemic/RichAccount.java", richCode);
        compileJavaSource(classesDir, "jsd/test/anemic/AnemicAccount.java", anemicCode);

        // RichAccount passes
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.anemic.RichAccount")
                .antiAnemic(true)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // AnemicAccount fails
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.anemic.AnemicAccount")
                .antiAnemic(true)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Class AnemicAccount is an anemic domain model with only getters/setters/constructors; it must define behavioral domain methods");
    }

    @Test
    @DisplayName("Test 23: CBO (Coupling Between Objects) enforces loose coupling threshold")
    void inspect_MaxCbo_EnforcesCouplingThreshold(@TempDir Path classesDir) throws Exception {
        String depA = "package jsd.test.cbo; public class ServiceA {}";
        String depB = "package jsd.test.cbo; public class ServiceB {}";
        String depC = "package jsd.test.cbo; public class ServiceC {}";

        String clientCode = """
                package jsd.test.cbo;
                public class ClientModule {
                    private ServiceA a = new ServiceA();
                    private ServiceB b = new ServiceB();
                    private ServiceC c = new ServiceC();
                }
                """;

        compileJavaSource(classesDir, "jsd/test/cbo/ServiceA.java", depA);
        compileJavaSource(classesDir, "jsd/test/cbo/ServiceB.java", depB);
        compileJavaSource(classesDir, "jsd/test/cbo/ServiceC.java", depC);
        compileJavaSource(classesDir, "jsd/test/cbo/ClientModule.java", clientCode);

        // Pass with limit 4
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.cbo.ClientModule")
                .maxCbo(4)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // Fail with limit 2 (client is coupled to 3 external classes)
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.cbo.ClientModule")
                .maxCbo(2)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Class ClientModule has Coupling Between Objects (CBO) of 3, exceeding maximum allowed limit (2)");
    }

    @Test
    @DisplayName("Test 24: Overloaded methods where one overload has @Override and one lacks it catches missing @Override")
    void inspect_RequireOverride_CatchesMissingOverrideOnOverloadedMethods(@TempDir Path classesDir) throws Exception {
        String baseCode = """
                package jsd.test.ovr;
                public interface MultiGreeter {
                    void greet(String name);
                    void greet(int times);
                }
                """;
        // PartialGreeter has @Override on greet(String), but lacks it on greet(int)
        String partialCode = """
                package jsd.test.ovr;
                public class PartialGreeter implements MultiGreeter {
                    @Override
                    public void greet(String name) {
                        System.out.println("Hello " + name);
                    }

                    public void greet(int times) {
                        System.out.println("Times " + times);
                    }
                }
                """;

        String fullCode = """
                package jsd.test.ovr;
                public class FullGreeter implements MultiGreeter {
                    @Override
                    public void greet(String name) {
                        System.out.println("Hello " + name);
                    }

                    @Override
                    public void greet(int times) {
                        System.out.println("Times " + times);
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/ovr/MultiGreeter.java", baseCode);
        compileJavaSource(classesDir, "jsd/test/ovr/PartialGreeter.java", partialCode);
        compileJavaSource(classesDir, "jsd/test/ovr/FullGreeter.java", fullCode);

        // FullGreeter passes when all overloads carry @Override
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.ovr.FullGreeter")
                .requireOverrideAnnotation(true)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();

        // PartialGreeter fails because greet(int) lacks @Override
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.ovr.PartialGreeter")
                .requireOverrideAnnotation(true)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Method 'greet' in class PartialGreeter overrides a method from MultiGreeter but is missing @Override annotation");
    }

    @Test
    @DisplayName("Test 25: Method line count fallback scanner accurately handles nested braces without premature truncation")
    void inspect_MethodLineCount_BraceDepthScannerHandlesNestedBlocks(@TempDir Path classesDir) throws Exception {
        String nestedMethodCode = """
                package jsd.test.lines;
                public class NestedBracesClass {
                    public void complexMethod(int a, int b) {
                        if (a > 0) {
                            for (int i = 0; i < 5; i++) {
                                if (b < 10) {
                                    System.out.println("nested " + i);
                                }
                            }
                        }
                        while (b > 0) {
                            b--;
                        }
                        System.out.println("done");
                    }
                }
                """;

        Path sourceFile = classesDir.resolve("jsd/test/lines/NestedBracesClass.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, nestedMethodCode);

        // 1. Direct unit verification on brace-depth scanner
        int lines = OopBytecodeMetricReader.countMethodLines(nestedMethodCode, "complexMethod");
        // complexMethod has 13 lines from '{' on line 3 to '}' on line 15
        assertThat(lines).isEqualTo(13);

        // 2. Fallback scan on source file
        int srcLines = OopBytecodeMetricReader.countMethodLinesFromSource(sourceFile, "complexMethod");
        assertThat(srcLines).isEqualTo(13);

        // 3. Compiler without debug info (-g:none) strips LineNumberTable, triggering fallback
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int compileResult = compiler.run(null, null, null,
                "-g:none",
                "-d", classesDir.toString(),
                "-cp", classesDir.toString(),
                sourceFile.toString());
        assertThat(compileResult).isZero();

        // Rule with maxLinesPerMethod = 10 should catch violation because method is 13 lines (not prematurely truncated to 4)
        OopConstraintRule failRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.lines.NestedBracesClass")
                .maxLinesPerMethod(10)
                .build();
        OopInspectionReport report = inspector.inspect(classesDir, List.of(failRule), 1.0);
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules().get(0).getMessage())
                .contains("Method 'complexMethod' in class NestedBracesClass has 13 lines, exceeding maximum allowed limit (10)");

        // Rule with maxLinesPerMethod = 20 should pass
        OopConstraintRule passRule = OopConstraintRule.builder()
                .targetClassName("jsd.test.lines.NestedBracesClass")
                .maxLinesPerMethod(20)
                .build();
        assertThat(inspector.inspect(classesDir, List.of(passRule), 1.0).isPassed()).isTrue();
    }

    @Test
    @DisplayName("Test 26: Anti-Anemic Domain Model cannot be bypassed by private or static helper methods")
    void inspect_AntiAnemic_CannotBeBypassedByPrivateOrStaticMethods(@TempDir Path classesDir) throws Exception {
        String bypassAttemptWithPrivate = """
                package jsd.test.anemic;
                public class DtoWithPrivateHelper {
                    private String data;
                    public DtoWithPrivateHelper(String data) { this.data = data; }
                    public String getData() { return data; }
                    public void setData(String data) { this.data = data; }

                    private void helper() {
                        // Attempting to bypass anti-anemic check with private method
                    }
                }
                """;

        String bypassAttemptWithStatic = """
                package jsd.test.anemic;
                public class DtoWithStaticHelper {
                    private int id;
                    public DtoWithStaticHelper(int id) { this.id = id; }
                    public int getId() { return id; }
                    public void setId(int id) { this.id = id; }

                    public static void utility() {
                        // Attempting to bypass anti-anemic check with static method
                    }
                }
                """;

        compileJavaSource(classesDir, "jsd/test/anemic/DtoWithPrivateHelper.java", bypassAttemptWithPrivate);
        compileJavaSource(classesDir, "jsd/test/anemic/DtoWithStaticHelper.java", bypassAttemptWithStatic);

        OopConstraintRule rulePrivate = OopConstraintRule.builder()
                .targetClassName("jsd.test.anemic.DtoWithPrivateHelper")
                .antiAnemic(true)
                .build();
        OopInspectionReport reportPrivate = inspector.inspect(classesDir, List.of(rulePrivate), 1.0);
        assertThat(reportPrivate.isPassed()).isFalse();
        assertThat(reportPrivate.getRules().get(0).getMessage())
                .contains("Class DtoWithPrivateHelper is an anemic domain model with only getters/setters/constructors; it must define behavioral domain methods");

        OopConstraintRule ruleStatic = OopConstraintRule.builder()
                .targetClassName("jsd.test.anemic.DtoWithStaticHelper")
                .antiAnemic(true)
                .build();
        OopInspectionReport reportStatic = inspector.inspect(classesDir, List.of(ruleStatic), 1.0);
        assertThat(reportStatic.isPassed()).isFalse();
        assertThat(reportStatic.getRules().get(0).getMessage())
                .contains("Class DtoWithStaticHelper is an anemic domain model with only getters/setters/constructors; it must define behavioral domain methods");
    }
}
