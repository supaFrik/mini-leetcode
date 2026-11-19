package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OOP Presets E2E Integration Test Suite")
public class OopPresetsE2EIntegrationTest {

    private OopGrindingInspector inspector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        inspector = new OopGrindingInspector(objectMapper);
    }

    private void compileJavaSources(Path outputDir, Map<String, String> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("System JavaCompiler must be available in JDK 17").isNotNull();

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(outputDir.toString());
        args.add("-cp");
        args.add(outputDir.toString());

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path sourcePath = outputDir.resolve(entry.getKey());
            if (sourcePath.getParent() != null) {
                Files.createDirectories(sourcePath.getParent());
            }
            Files.writeString(sourcePath, entry.getValue());
            args.add(sourcePath.toString());
        }

        int result = compiler.run(null, null, null, args.toArray(new String[0]));
        assertThat(result).as("Dynamic compilation of Java test sources must succeed").isZero();
    }

    private OopConstraintRule ruleFromJson(String json) {
        try {
            return objectMapper.readValue(json, OopConstraintRule.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize rule JSON: " + json, e);
        }
    }

    private OopConstraintRule createEncapsulationPreset(String targetClassName) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-encapsulation",
                    "ruleName": "Encapsulation: fields must be private or protected",
                    "targetClassName": "%s",
                    "enforceEncapsulation": true,
                    "weight": 1.0
                }
                """, targetClassName));
    }

    private OopConstraintRule createPolymorphismPreset(String targetClassName) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-polymorphism",
                    "ruleName": "Polymorphism: requires interface or abstract class",
                    "targetClassName": "%s",
                    "expectedType": "ABSTRACT_CLASS_OR_INTERFACE",
                    "weight": 1.0
                }
                """, targetClassName));
    }

    private OopConstraintRule createAntiMonolithicPreset(String targetClassName, int minClasses, boolean disallowMonolithicMain) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-anti-monolithic",
                    "ruleName": "Anti-Monolithic: multi-class design",
                    "targetClassName": "%s",
                    "minClassCount": %d,
                    "disallowMonolithicMain": %b,
                    "weight": 1.0
                }
                """, targetClassName, minClasses, disallowMonolithicMain));
    }

    private OopConstraintRule createStrictImmutabilityPreset(String targetClassName) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-strict-immutability",
                    "ruleName": "Strict Immutability & Finality",
                    "targetClassName": "%s",
                    "requireFinalFields": true,
                    "weight": 1.0
                }
                """, targetClassName));
    }

    private OopConstraintRule createCleanCodeLimitsPreset(String targetClassName, int maxMethods, int maxLines) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-clean-code-limits",
                    "ruleName": "Clean Code / Size Limits",
                    "targetClassName": "%s",
                    "maxMethodsPerClass": %d,
                    "maxLinesPerMethod": %d,
                    "weight": 1.0
                }
                """, targetClassName, maxMethods, maxLines));
    }

    private OopConstraintRule createRequireOverridePreset(String targetClassName) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-require-override",
                    "ruleName": "Mandatory @Override Annotation",
                    "targetClassName": "%s",
                    "requireOverrideAnnotation": true,
                    "weight": 1.0
                }
                """, targetClassName));
    }

    private OopConstraintRule createAntiAnemicPreset(String targetClassName) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-anti-anemic",
                    "ruleName": "Anti-Anemic Domain Model",
                    "targetClassName": "%s",
                    "antiAnemic": true,
                    "weight": 1.0
                }
                """, targetClassName));
    }

    private OopConstraintRule createLooseCouplingPreset(String targetClassName, int maxCbo) {
        return ruleFromJson(String.format("""
                {
                    "ruleId": "preset-loose-coupling",
                    "ruleName": "Loose Coupling (CBO Limit)",
                    "targetClassName": "%s",
                    "maxCbo": %d,
                    "weight": 1.0
                }
                """, targetClassName, maxCbo));
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (Wildcard, Encapsulation, Polymorphism, Anti-Monolithic, Scoping)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Feature Coverage")
    class Tier1FeatureCoverage {

        @Test
        @DisplayName("T1.1: Wildcard * discovery scans and evaluates multiple classes across packages")
        void wildcardDiscovery_MultipleClassesAcrossPackages_DiscoversAll(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/model/Account.java", """
                    package com.example.model;
                    public class Account {
                        private String id;
                        private double balance;
                        public Account(String id, double balance) { this.id = id; this.balance = balance; }
                        public double getBalance() { return balance; }
                    }
                    """);
            sources.put("com/example/service/AccountService.java", """
                    package com.example.service;
                    import com.example.model.Account;
                    public class AccountService {
                        private Account account;
                        public AccountService(Account account) { this.account = account; }
                        public double fetchBalance() { return account.getBalance(); }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).hasSize(1);
            assertThat(report.getRules().get(0).isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.2: Wildcard * evaluates all discovered classes and flags violation in offending class")
        void wildcardDiscovery_EvaluatesAllDiscoveredClasses_FindsViolationInAnyClass(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/CleanA.java", """
                    package com.example;
                    public class CleanA {
                        private int a;
                    }
                    """);
            sources.put("com/example/CleanB.java", """
                    package com.example;
                    public class CleanB {
                        protected String b;
                    }
                    """);
            sources.put("com/example/LeakyC.java", """
                    package com.example;
                    public class LeakyC {
                        public double balance; // VIOLATION
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            OopRuleResult result = report.getRules().get(0);
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessage()).contains("balance");
            assertThat(result.getMessage()).contains("LeakyC");
        }

        @Test
        @DisplayName("T1.3: Target class specific scoping evaluates only target class, ignoring others")
        void targetClassScoping_EvaluatesOnlyTargetClassIgnoringOthers(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Account.java", """
                    public class Account {
                        private double balance;
                        public Account(double balance) { this.balance = balance; }
                        public double getBalance() { return balance; }
                    }
                    """);
            sources.put("UnrelatedHelper.java", """
                    public class UnrelatedHelper {
                        public int leakyField; // Leaky, but should be ignored by scoped rule
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule scopedRule = createEncapsulationPreset("Account");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(scopedRule), 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("T1.4: Target class missing in submission reports clear violation")
        void targetClassScoping_MissingRequiredClass_FailsWithDescriptiveError(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Customer.java", """
                    public class Customer {
                        private String name;
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule scopedRule = createEncapsulationPreset("Account");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(scopedRule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            assertThat(report.getRules().get(0).getMessage()).contains("Account").contains("not found");
        }

        @Test
        @DisplayName("T1.5: Encapsulation preset passes when all fields are private")
        void encapsulation_AllPrivateFields_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "User.java", """
                            public class User {
                                private String username;
                                private String passwordHash;
                                public User(String u, String p) { this.username = u; this.passwordHash = p; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.6: Encapsulation preset passes when fields are protected for inheritance")
        void encapsulation_ProtectedFieldsInInheritance_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "BaseEntity.java", """
                            public abstract class BaseEntity {
                                protected long id;
                                protected String createdAt;
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.7: Encapsulation preset passes when fields are public static final constants")
        void encapsulation_PublicStaticFinalConstants_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Constants.java", """
                            public class Constants {
                                public static final String CURRENCY = "VND";
                                public static final double TAX_RATE = 0.08;
                                private int sessionTimeout = 30;
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.8: Encapsulation preset fails on public non-final field")
        void encapsulation_PublicNonFinalField_FailsWithViolation(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Wallet.java", """
                            public class Wallet {
                                public double balance; // Public mutable field violation!
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("balance");
        }

        @Test
        @DisplayName("T1.9: Encapsulation preset fails on package-private mutable field")
        void encapsulation_PackagePrivateMutableField_FailsWithViolation(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Order.java", """
                            public class Order {
                                double totalAmount; // Package-private field violation!
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("totalAmount");
        }

        @Test
        @DisplayName("T1.10: Polymorphism preset passes when an interface is declared")
        void polymorphism_InterfacePresent_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Shape.java", """
                            public interface Shape {
                                double getArea();
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createPolymorphismPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.11: Polymorphism preset passes when an abstract class is declared")
        void polymorphism_AbstractClassPresent_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "AbstractShape.java", """
                            public abstract class AbstractShape {
                                public abstract double getArea();
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createPolymorphismPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.12: Polymorphism preset passes when class implements interface or extends abstract class")
        void polymorphism_ClassImplementsInterface_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Drivable.java", """
                    public interface Drivable {
                        void drive();
                    }
                    """);
            sources.put("Car.java", """
                    public class Car implements Drivable {
                        @Override
                        public void drive() {}
                    }
                    """);
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createPolymorphismPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.13: Polymorphism preset fails when only independent concrete classes exist")
        void polymorphism_OnlyIndependentConcreteClasses_Fails(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "User.java", """
                            public class User {
                                private String name;
                            }
                            """,
                    "Role.java", """
                            public class Role {
                                private String roleName;
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createPolymorphismPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).containsIgnoringCase("polymorphism");
        }

        @Test
        @DisplayName("T1.14: Anti-monolithic preset passes with >= 2 classes and main delegation")
        void antiMonolithic_MultiClassWithMainDelegation_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Calculator.java", """
                    public class Calculator {
                        public int add(int a, int b) { return a + b; }
                    }
                    """);
            sources.put("Main.java", """
                    public class Main {
                        public static void main(String[] args) {
                            Calculator calc = new Calculator();
                            System.out.println(calc.add(2, 3));
                        }
                    }
                    """);
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 2, true);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.15: Anti-monolithic preset fails on single-class monolithic submission")
        void antiMonolithic_SingleClassSubmission_FailsWithClassCountViolation(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Main.java", """
                            public class Main {
                                public static void main(String[] args) {
                                    int a = 1;
                                    int b = 2;
                                    System.out.println(a + b);
                                }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 2, true);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("at least 2 classes");
        }

        @Test
        @DisplayName("T1.16: Anti-monolithic preset fails when main does not delegate to declared domain classes")
        void antiMonolithic_MainDoesNotDelegateToDomainClasses_Fails(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("UnusedWorker.java", """
                    public class UnusedWorker {
                        public void doWork() {}
                    }
                    """);
            sources.put("Main.java", """
                    public class Main {
                        public static void main(String[] args) {
                            // Monolithic logic that never references UnusedWorker
                            int sum = 0;
                            for (int i = 0; i < 100; i++) {
                                sum += i;
                            }
                            System.out.println("Result: " + sum);
                        }
                    }
                    """);
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 2, true);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).containsIgnoringCase("main");
        }

        @Test
        @DisplayName("T1.17: Anti-monolithic configurable minClassCount passes when satisfied")
        void antiMonolithic_ConfigurableMinClassCount_PassesWhenSatisfied(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "ClassA.java", "public class ClassA {}",
                    "ClassB.java", "public class ClassB {}",
                    "ClassC.java", "public class ClassC {}"
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 3, false);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.18: Anti-monolithic configurable minClassCount fails when count is insufficient")
        void antiMonolithic_ConfigurableMinClassCount_FailsWhenInsufficient(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "ClassA.java", "public class ClassA {}",
                    "ClassB.java", "public class ClassB {}"
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 3, false);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("at least 3 classes");
        }

        @Test
        @DisplayName("T1.19: Strict Immutability preset passes for compliant immutable class")
        void strictImmutability_CompliantClass_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Price.java", """
                            public class Price {
                                private final long amount;
                                private final String currency;
                                public Price(long amount, String currency) {
                                    this.amount = amount;
                                    this.currency = currency;
                                }
                                public long getAmount() { return amount; }
                                public String getCurrency() { return currency; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createStrictImmutabilityPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.20: Clean Code limits preset passes within method count and line count")
        void cleanCodeLimits_WithinBounds_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "MathHelper.java", """
                            public class MathHelper {
                                public int add(int a, int b) { return a + b; }
                                public int sub(int a, int b) { return a - b; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createCleanCodeLimitsPreset("*", 5, 20);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.21: Mandatory @Override preset passes when all overrides have annotation")
        void requireOverride_CompliantOverrides_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Printable.java", "public interface Printable { void print(); }",
                    "Doc.java", """
                            public class Doc implements Printable {
                                @Override
                                public void print() { System.out.println("doc"); }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createRequireOverridePreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.22: Anti-Anemic domain model preset passes for rich model with behavior")
        void antiAnemic_RichDomainModel_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Account.java", """
                            public class Account {
                                private double balance;
                                public Account(double b) { this.balance = b; }
                                public double getBalance() { return balance; }
                                public void deposit(double a) { if (a > 0) balance += a; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createAntiAnemicPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("T1.23: Loose Coupling preset passes when CBO is within threshold")
        void looseCoupling_WithinCboThreshold_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Dep1.java", "public class Dep1 {}",
                    "Client.java", """
                            public class Client {
                                private Dep1 dep = new Dep1();
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createLooseCouplingPreset("*", 2);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isTrue();
        }
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Boundary & Corner Cases")
    class Tier2BoundaryAndCornerCases {

        @Test
        @DisplayName("B1: Empty classes without fields or methods evaluate gracefully without NPE")
        void boundary_EmptyClassesWithoutFieldsOrMethods(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "EmptyA.java", "public class EmptyA {}",
                    "EmptyB.java", "public class EmptyB {}"
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule encapsulationRule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(encapsulationRule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("B2: Classes declared in deep package structures evaluate correctly")
        void boundary_DeepPackageDeclarations(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "com/corporate/enterprise/banking/v1/model/CustomerEntity.java", """
                            package com.corporate.enterprise.banking.v1.model;
                            public class CustomerEntity {
                                private String customerId;
                                public CustomerEntity(String id) { this.customerId = id; }
                                public String getCustomerId() { return customerId; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("B3: Nested static classes and anonymous inner classes ($1) handled with anonymous skipped")
        void boundary_NestedAndAnonymousInnerClasses(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Outer.java", """
                            public class Outer {
                                private int outerVal;

                                public static class Inner {
                                    private int innerVal;
                                    public int getInnerVal() { return innerVal; }
                                }

                                public void runAnonymous() {
                                    Runnable r = new Runnable() {
                                        @Override
                                        public void run() {
                                            System.out.println("Anonymous worker");
                                        }
                                    };
                                    r.run();
                                }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isTrue();
        }

        @Test
        @DisplayName("B4: Multiple classes in a single .java file compile into multiple .class and are all inspected")
        void boundary_MultipleClassesInSingleJavaFile(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Combined.java", """
                            public class Combined {
                                private String mainData;
                            }
                            class PackagePrivateRecord {
                                public int leakyField; // VIOLATION in second class of same file
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createEncapsulationPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("leakyField");
        }

        @Test
        @DisplayName("B5: Empty classes directory returns report indicating zero classes gracefully")
        void boundary_EmptyClassesDirectory_ReportsZeroClassesGracefully(@TempDir Path classesDir) {
            OopConstraintRule rule = createAntiMonolithicPreset("*", 2, true);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .satisfiesAnyOf(
                            msg -> assertThat(msg).contains("No user classes found"),
                            msg -> assertThat(msg).contains("expected at least 2 classes, but found 0")
                    );
        }

        @Test
        @DisplayName("B6: Strict Immutability flags non-final field and setter violations")
        void boundary_StrictImmutability_FlagsNonFinalFieldAndSetter(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Mutable.java", """
                            public class Mutable {
                                private int x;
                                public void setX(int val) { this.x = val; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createStrictImmutabilityPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .contains("Field 'x' in class Mutable must be final (immutability violation)")
                    .contains("must not declare setter for immutable field 'x'");
        }

        @Test
        @DisplayName("B7: Clean Code limits flags method count and lines per method violations")
        void boundary_CleanCodeLimits_FlagsMethodCountAndLines(@TempDir Path classesDir) throws Exception {
            StringBuilder sb = new StringBuilder();
            sb.append("public class BigModule {\n");
            for (int m = 1; m <= 5; m++) {
                sb.append("    public void m").append(m).append("() {\n");
                sb.append("        int x = 0;\n");
                for (int l = 0; l < 15; l++) {
                    sb.append("        x += ").append(l).append(";\n");
                }
                sb.append("    }\n");
            }
            sb.append("}\n");
            compileJavaSources(classesDir, Map.of("BigModule.java", sb.toString()));

            OopConstraintRule rule = createCleanCodeLimitsPreset("*", 3, 10);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .contains("exceeding maximum allowed limit (3)")
                    .contains("exceeding maximum allowed limit (10)");
        }

        @Test
        @DisplayName("B8: Missing @Override annotation fails check with exact supertype info")
        void boundary_RequireOverride_MissingAnnotationFails(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Action.java", "public interface Action { void execute(); }",
                    "RunAction.java", """
                            public class RunAction implements Action {
                                public void execute() {} // missing @Override
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createRequireOverridePreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .contains("Method 'execute' in class RunAction overrides a method from Action but is missing @Override annotation");
        }

        @Test
        @DisplayName("B9: Anemic domain model fails check with descriptive message")
        void boundary_AntiAnemic_FailsAnemicDomainModel(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "ProductDto.java", """
                            public class ProductDto {
                                private String sku;
                                private double price;
                                public ProductDto(String s, double p) { this.sku = s; this.price = p; }
                                public String getSku() { return sku; }
                                public void setSku(String s) { this.sku = s; }
                                public double getPrice() { return price; }
                                public void setPrice(double p) { this.price = p; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createAntiAnemicPreset("*");
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .contains("Class ProductDto is an anemic domain model with only getters/setters/constructors; it must define behavioral domain methods");
        }

        @Test
        @DisplayName("B10: High CBO coupling fails check with exact count")
        void boundary_LooseCoupling_HighCboFails(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "A.java", "public class A {}",
                    "B.java", "public class B {}",
                    "C.java", "public class C {}",
                    "HighCbo.java", """
                            public class HighCbo {
                                private A a = new A();
                                private B b = new B();
                                private C c = new C();
                            }
                            """
            );
            compileJavaSources(classesDir, sources);
            OopConstraintRule rule = createLooseCouplingPreset("HighCbo", 2);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage())
                    .contains("Class HighCbo has Coupling Between Objects (CBO) of 3, exceeding maximum allowed limit (2)");
        }
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: Cross-Feature Combinations")
    class Tier3CrossFeatureCombinations {

        @Test
        @DisplayName("C1: All 3 Presets enabled simultaneously on compliant OOP project achieve 100% score")
        void crossFeature_AllThreePresetsActive_FullyCompliant(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Account.java", """
                    public interface Account {
                        double getBalance();
                        void deposit(double amount);
                    }
                    """);
            sources.put("SavingsAccount.java", """
                    public class SavingsAccount implements Account {
                        private double balance;
                        public SavingsAccount(double initial) { this.balance = initial; }
                        @Override public double getBalance() { return balance; }
                        @Override public void deposit(double amount) { this.balance += amount; }
                    }
                    """);
            sources.put("BankApp.java", """
                    public class BankApp {
                        public static void main(String[] args) {
                            Account acc = new SavingsAccount(100.0);
                            acc.deposit(50.0);
                            System.out.println("Balance: " + acc.getBalance());
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).hasSize(3);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }

        @Test
        @DisplayName("C2: All 3 Presets active, partial violation isolates only failing rule while others pass")
        void crossFeature_AllThreePresetsActive_PartialViolation_IsolatesFailingRule(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Account.java", """
                    public interface Account {
                        double getBalance();
                    }
                    """);
            sources.put("SavingsAccount.java", """
                    public class SavingsAccount implements Account {
                        public double balance; // ENCAPSULATION VIOLATION
                        public SavingsAccount(double initial) { this.balance = initial; }
                        @Override public double getBalance() { return balance; }
                    }
                    """);
            sources.put("BankApp.java", """
                    public class BankApp {
                        public static void main(String[] args) {
                            Account acc = new SavingsAccount(100.0);
                            System.out.println("Balance: " + acc.getBalance());
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isFalse();
            // Encapsulation failed
            assertThat(report.getRules().get(0).isPassed()).isFalse();
            // Polymorphism passed
            assertThat(report.getRules().get(1).isPassed()).isTrue();
            // Anti-monolithic passed
            assertThat(report.getRules().get(2).isPassed()).isTrue();
        }

        @Test
        @DisplayName("C3: Preset combined with custom method constraint enforces both")
        void crossFeature_PresetWithCustomMethodConstraint(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "Wallet.java", """
                            public class Wallet {
                                private double cash;
                                public Wallet(double cash) { this.cash = cash; }
                                public double getCash() { return cash; }
                            }
                            """
            );
            compileJavaSources(classesDir, sources);

            OopConstraintRule encapsulationPreset = createEncapsulationPreset("*");
            OopConstraintRule customMethodRule = OopConstraintRule.builder()
                    .ruleName("WALLET_METHOD_CHECK")
                    .targetClassName("Wallet")
                    .methods(List.of(
                            OopMethodConstraint.builder()
                                    .name("getCash")
                                    .returnType("double")
                                    .requiredModifiers(List.of("public"))
                                    .build()
                    ))
                    .weight(1.0)
                    .build();

            OopInspectionReport report = inspector.inspect(classesDir, List.of(encapsulationPreset, customMethodRule), 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getRules()).hasSize(2);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }

        @Test
        @DisplayName("C4: Targeted encapsulation rule combined with wildcard anti-monolithic rule")
        void crossFeature_TargetClassEncapsulationWithWildcardAntiMonolithic(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Customer.java", """
                    public class Customer {
                        private String id;
                        public Customer(String id) { this.id = id; }
                    }
                    """);
            sources.put("LogHelper.java", """
                    public class LogHelper {
                        public String level = "INFO"; // Not scoped by Customer rule
                    }
                    """);
            sources.put("Main.java", """
                    public class Main {
                        public static void main(String[] args) {
                            Customer c = new Customer("C123");
                            LogHelper h = new LogHelper();
                            System.out.println(h.level);
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule scopedEncapsulation = createEncapsulationPreset("Customer");
            OopConstraintRule wildcardAntiMonolithic = createAntiMonolithicPreset("*", 2, true);

            OopInspectionReport report = inspector.inspect(classesDir, List.of(scopedEncapsulation, wildcardAntiMonolithic), 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }

        @Test
        @DisplayName("C5: Full Strict OOP composite preset passes fully compliant architecture")
        void crossFeature_FullStrictOop_CompliantArchitecture_Passes(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Payment.java", """
                    public interface Payment {
                        long process(long amount);
                    }
                    """);
            sources.put("CreditPayment.java", """
                    public class CreditPayment implements Payment {
                        private final String cardToken;
                        public CreditPayment(String cardToken) { this.cardToken = cardToken; }
                        public String getCardToken() { return cardToken; }
                        @Override
                        public long process(long amount) {
                            return amount > 0 ? amount : 0;
                        }
                    }
                    """);
            sources.put("PaymentApp.java", """
                    public class PaymentApp {
                        public static void main(String[] args) {
                            Payment p = new CreditPayment("tok_123");
                            System.out.println(p.process(100));
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true),
                    createStrictImmutabilityPreset("CreditPayment"),
                    createCleanCodeLimitsPreset("*", 10, 30),
                    createRequireOverridePreset("*"),
                    createAntiAnemicPreset("CreditPayment"),
                    createLooseCouplingPreset("PaymentApp", 4)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).hasSize(8);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }

        @Test
        @DisplayName("C6: Multi-preset with Strict Immutability and Size Limits isolates failing rules")
        void crossFeature_MultiPreset_IsolatesImmutabilityAndSizeViolations(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Order.java", """
                    public class Order {
                        private long amount; // mutable -> violation
                        public Order(long amt) { this.amount = amt; }
                        public long getAmount() { return amount; }
                        public void setAmount(long amt) { this.amount = amt; } // setter -> violation
                        public void extra1() {}
                        public void extra2() {}
                        public void extra3() {}
                    }
                    """);
            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createStrictImmutabilityPreset("Order"),
                    createCleanCodeLimitsPreset("Order", 3, 20) // has 5 methods -> violation
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules()).hasSize(2);
            assertThat(report.getRules()).allMatch(r -> !r.isPassed());
        }
    }

    // =========================================================================
    // TIER 4: REAL-WORLD STUDENT SUBMISSIONS
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: Real-World Student Submissions")
    class Tier4RealWorldWorkloads {

        @Test
        @DisplayName("S1: Modular Bank System student submission passes all preset grading criteria")
        void realWorld_BankSystem_CompliantModularSubmission(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Account.java", """
                    package bank;
                    public class Account {
                        private String accountNumber;
                        private double balance;
                        public Account(String accNum, double initial) {
                            this.accountNumber = accNum;
                            this.balance = initial;
                        }
                        public double getBalance() { return balance; }
                        public void deposit(double amount) { if (amount > 0) balance += amount; }
                    }
                    """);
            sources.put("Transaction.java", """
                    package bank;
                    public class Transaction {
                        private String txId;
                        private double amount;
                        public Transaction(String id, double amt) { this.txId = id; this.amount = amt; }
                        public double getAmount() { return amount; }
                    }
                    """);
            sources.put("BankMain.java", """
                    package bank;
                    public class BankMain {
                        public static void main(String[] args) {
                            Account acc = new Account("ACC001", 1000.0);
                            Transaction tx = new Transaction("TX01", 200.0);
                            acc.deposit(tx.getAmount());
                            System.out.println("New balance: " + acc.getBalance());
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("S2: Monolithic Bank submission with public static variables fails presets")
        void realWorld_BankSystem_MonolithicSubmission(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = Map.of(
                    "BankMain.java", """
                            public class BankMain {
                                public static double balance = 1000.0;
                                public static String accountNumber = "ACC001";

                                public static void main(String[] args) {
                                    double depositAmount = 200.0;
                                    balance += depositAmount;
                                    System.out.println("Balance: " + balance);
                                }
                            }
                            """
            );

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).isPassed()).isFalse(); // Encapsulation failed
            assertThat(report.getRules().get(1).isPassed()).isFalse(); // Anti-monolithic failed
        }

        @Test
        @DisplayName("S3: Polymorphic Shape Hierarchy student submission passes all 3 presets")
        void realWorld_ShapeHierarchy_PolymorphicSubmission(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("Shape.java", """
                    public interface Shape {
                        double area();
                        double perimeter();
                    }
                    """);
            sources.put("AbstractShape.java", """
                    public abstract class AbstractShape implements Shape {
                        protected String color;
                        public AbstractShape(String color) { this.color = color; }
                        public String getColor() { return color; }
                    }
                    """);
            sources.put("Circle.java", """
                    public class Circle extends AbstractShape {
                        private double radius;
                        public Circle(String color, double r) { super(color); this.radius = r; }
                        @Override public double area() { return Math.PI * radius * radius; }
                        @Override public double perimeter() { return 2 * Math.PI * radius; }
                    }
                    """);
            sources.put("Rectangle.java", """
                    public class Rectangle extends AbstractShape {
                        private double width;
                        private double height;
                        public Rectangle(String color, double w, double h) {
                            super(color);
                            this.width = w;
                            this.height = h;
                        }
                        @Override public double area() { return width * height; }
                        @Override public double perimeter() { return 2 * (width + height); }
                    }
                    """);
            sources.put("ShapeApp.java", """
                    import java.util.List;
                    public class ShapeApp {
                        public static void main(String[] args) {
                            List<Shape> shapes = List.of(new Circle("red", 5.0), new Rectangle("blue", 4.0, 6.0));
                            double totalArea = 0;
                            for (Shape s : shapes) {
                                totalArea += s.area();
                            }
                            System.out.println("Total area: " + totalArea);
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }

        @Test
        @DisplayName("S4: Payment Processor with multiple implementations passes all presets")
        void realWorld_PaymentProcessor_MultipleImplementations(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("PaymentMethod.java", """
                    public interface PaymentMethod {
                        boolean pay(double amount);
                    }
                    """);
            sources.put("CreditCardPayment.java", """
                    public class CreditCardPayment implements PaymentMethod {
                        private String cardNumber;
                        public CreditCardPayment(String card) { this.cardNumber = card; }
                        @Override public boolean pay(double amount) { return amount > 0; }
                    }
                    """);
            sources.put("PayPalPayment.java", """
                    public class PayPalPayment implements PaymentMethod {
                        private String email;
                        public PayPalPayment(String email) { this.email = email; }
                        @Override public boolean pay(double amount) { return amount > 0; }
                    }
                    """);
            sources.put("CheckoutApp.java", """
                    public class CheckoutApp {
                        public static void main(String[] args) {
                            PaymentMethod pm = new CreditCardPayment("1234-5678");
                            pm.pay(99.0);
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("S5: Cheating attempt with fake unused class fails main delegation check")
        void realWorld_CheatingAttempt_MonolithicMainWithFakeUnusedClass(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("DummyClass.java", """
                    public class DummyClass {
                        // Empty class created by student to attempt bypassing classCount >= 2
                    }
                    """);
            sources.put("MonolithicMain.java", """
                    public class MonolithicMain {
                        public static void main(String[] args) {
                            // Entire monolithic computation with 30+ statements and no reference to DummyClass
                            int[] arr = new int[]{5, 2, 8, 1, 9};
                            for (int i = 0; i < arr.length; i++) {
                                for (int j = i + 1; j < arr.length; j++) {
                                    if (arr[i] > arr[j]) {
                                        int tmp = arr[i];
                                        arr[i] = arr[j];
                                        arr[j] = tmp;
                                    }
                                }
                            }
                            for (int val : arr) {
                                System.out.print(val + " ");
                            }
                            System.out.println();
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            OopConstraintRule rule = createAntiMonolithicPreset("*", 2, true);
            OopInspectionReport report = inspector.inspect(classesDir, List.of(rule), 1.0);

            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).containsIgnoringCase("main");
        }

        @Test
        @DisplayName("S6: Polymorphic E-Commerce payment processing scenario with strict OOP presets")
        void realWorld_EcommercePaymentProcessing_PassesAllStrictOopPresets(@TempDir Path classesDir) throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("payment/PaymentGateway.java", """
                    package payment;
                    public interface PaymentGateway {
                        boolean executeTransaction(long cents);
                    }
                    """);
            sources.put("payment/StripeGateway.java", """
                    package payment;
                    public class StripeGateway implements PaymentGateway {
                        private final String apiKey;
                        public StripeGateway(String key) { this.apiKey = key; }
                        public String getApiKey() { return apiKey; }
                        @Override
                        public boolean executeTransaction(long cents) {
                            return cents > 0 && apiKey != null;
                        }
                    }
                    """);
            sources.put("payment/CheckoutService.java", """
                    package payment;
                    public class CheckoutService {
                        private final PaymentGateway gateway;
                        public CheckoutService(PaymentGateway gw) { this.gateway = gw; }
                        public boolean checkout(long totalCents) {
                            return gateway.executeTransaction(totalCents);
                        }
                    }
                    """);
            sources.put("payment/EcommerceMain.java", """
                    package payment;
                    public class EcommerceMain {
                        public static void main(String[] args) {
                            PaymentGateway gw = new StripeGateway("sk_test_123");
                            CheckoutService s = new CheckoutService(gw);
                            boolean ok = s.checkout(4999);
                            System.out.println("Checkout status: " + ok);
                        }
                    }
                    """);

            compileJavaSources(classesDir, sources);

            List<OopConstraintRule> rules = List.of(
                    createEncapsulationPreset("*"),
                    createPolymorphismPreset("*"),
                    createAntiMonolithicPreset("*", 2, true),
                    createStrictImmutabilityPreset("payment.StripeGateway"),
                    createCleanCodeLimitsPreset("*", 10, 25),
                    createRequireOverridePreset("*"),
                    createAntiAnemicPreset("payment.StripeGateway"),
                    createLooseCouplingPreset("payment.CheckoutService", 4)
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 1.0);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).hasSize(8);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }
    }
}
