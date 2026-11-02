package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopJUnit5TestRunner;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Comprehensive Adversarial Stress Test Suite for OopGrindingInspector & OopJUnit5TestRunner.
 *
 * Scenarios tested:
 * 1. Malicious static initializers (class with runtime exception, System.exit, destructive static field init)
 * 2. Circular class dependencies across packages
 * 3. Deep interface inheritance hierarchy and multiple interfaces
 * 4. Obscure modifiers: native, synchronized, strictfp, volatile, transient
 * 5. Subclass missing required abstract method implementations or misdeclaring abstract status
 * 6. Missing compiled class (ClassNotFoundException handled cleanly as rule failure)
 * 7. Incompatible bytecode or linkage errors (LinkageError shielded, corrupted class files, class name mismatch)
 * 8. Extreme rule weights (0.0, very large numbers, negative) and boundary rules JSON
 * 9. OopJUnit5TestRunner adversarial edge cases (timeouts, unhandled exceptions, missing test classes)
 */
class OopGrindingInspectorAdversarialTest {

    private OopGrindingInspector inspector;
    private OopJUnit5TestRunner testRunner;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        inspector = new OopGrindingInspector(objectMapper);
        testRunner = new OopJUnit5TestRunner();
    }

    // Helper to compile a single Java file
    private void compileJavaSource(Path outputDir, String fileName, String sourceCode) throws IOException {
        compileJavaSources(outputDir, Map.of(fileName, sourceCode));
    }

    // Helper to compile multiple Java files simultaneously (crucial for circular dependencies)
    private void compileJavaSources(Path outputDir, Map<String, String> sources) throws IOException {
        List<String> args = new ArrayList<>(List.of(
                "-d", outputDir.toString(),
                "-cp", outputDir.toString() + System.getProperty("path.separator") + System.getProperty("java.class.path")
        ));

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path sourcePath = outputDir.resolve(entry.getKey());
            if (sourcePath.getParent() != null) {
                Files.createDirectories(sourcePath.getParent());
            }
            Files.writeString(sourcePath, entry.getValue());
            args.add(sourcePath.toString());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("System JavaCompiler should be available").isNotNull();

        int result = compiler.run(null, null, null, args.toArray(new String[0]));
        assertThat(result).as("Batch compilation of sources should succeed").isZero();
    }

    @Nested
    @DisplayName("Scenario 1: Malicious Static Initializers & clinit Shielding")
    class MaliciousStaticInitializerTests {

        @Test
        @DisplayName("1.1: Static initializer with RuntimeException must NOT trigger during reflection audit")
        void inspect_StaticBlockThrowsRuntimeException_ShieldedWithoutExecuting(@TempDir Path classesDir) throws Exception {
            String evilCode = """
                    package jsd.test.evil;
                    public class ExplodingClass {
                        static {
                            if (true) {
                                throw new RuntimeException("MALICIOUS_STATIC_INIT_EXPLODED");
                            }
                        }
                        private String name;
                        public ExplodingClass() { this.name = "safe"; }
                        public String getName() { return name; }
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/evil/ExplodingClass.java", evilCode);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("EXPLODING_CLASS_AUDIT")
                            .targetClassName("jsd.test.evil.ExplodingClass")
                            .expectedType(ExpectedClassType.CLASS)
                            .requiredModifiers(List.of("public"))
                            .enforceEncapsulation(true)
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("getName").returnType("String").build()
                            ))
                            .weight(1.0)
                            .build()
            );

            // initialize = false must ensure no exception occurs
            assertThatNoException().isThrownBy(() -> {
                OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
                assertThat(report.isPassed()).isTrue();
                assertThat(report.getScore()).isEqualTo(100.0);
            });
        }

        @Test
        @DisplayName("1.2: Static initializer with System.exit() must NOT terminate the host JVM")
        void inspect_StaticBlockCallsSystemExit_ShieldedWithoutTerminatingJvm(@TempDir Path classesDir) throws Exception {
            String exitBombCode = """
                    package jsd.test.evil;
                    public class ExitBombClass {
                        static {
                            if (System.currentTimeMillis() > 0) {
                                // If initialize was true, this would terminate the JVM!
                                System.exit(42);
                            }
                        }
                        public void regularMethod() {}
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/evil/ExitBombClass.java", exitBombCode);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("EXIT_BOMB_AUDIT")
                            .targetClassName("jsd.test.evil.ExitBombClass")
                            .expectedType(ExpectedClassType.CLASS)
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("regularMethod").returnType("void").build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("1.3: Static field initializer throwing NullPointerException must NOT execute")
        void inspect_StaticFieldExpressionThrowsException_ShieldedWithoutExecuting(@TempDir Path classesDir) throws Exception {
            String bombCode = """
                    package jsd.test.evil;
                    public class StaticFieldBomb {
                        public static final String EXPLOSIVE = ((String) null).toLowerCase();
                        private int id;
                        public int getId() { return id; }
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/evil/StaticFieldBomb.java", bombCode);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("STATIC_FIELD_BOMB_AUDIT")
                            .targetClassName("jsd.test.evil.StaticFieldBomb")
                            .expectedType(ExpectedClassType.CLASS)
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Scenario 2: Circular Class Dependencies Across Packages")
    class CircularDependencyTests {

        @Test
        @DisplayName("2.1: Circular cross-package dependencies between ClassA and ClassB inspected without deadlock or stack overflow")
        void inspect_CircularDependenciesAcrossPackages_AuditedSuccessfully(@TempDir Path classesDir) throws Exception {
            String classACode = """
                    package jsd.test.pkgA;
                    import jsd.test.pkgB.ClassB;

                    public class ClassA {
                        private ClassB friendB;
                        public ClassA() {}
                        public ClassB getFriendB() { return friendB; }
                        public void setFriendB(ClassB friendB) { this.friendB = friendB; }
                    }
                    """;

            String classBCode = """
                    package jsd.test.pkgB;
                    import jsd.test.pkgA.ClassA;

                    public class ClassB {
                        private ClassA friendA;
                        public ClassB() {}
                        public ClassA getFriendA() { return friendA; }
                        public void setFriendA(ClassA friendA) { this.friendA = friendA; }
                    }
                    """;

            compileJavaSources(classesDir, Map.of(
                    "jsd/test/pkgA/ClassA.java", classACode,
                    "jsd/test/pkgB/ClassB.java", classBCode
            ));

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("RULE_CLASS_A")
                            .targetClassName("jsd.test.pkgA.ClassA")
                            .expectedType(ExpectedClassType.CLASS)
                            .enforceEncapsulation(true)
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("getFriendB").returnType("ClassB").build(),
                                    OopMethodConstraint.builder().name("setFriendB").parameterTypes(List.of("ClassB")).build()
                            ))
                            .weight(1.0)
                            .build(),
                    OopConstraintRule.builder()
                            .ruleName("RULE_CLASS_B")
                            .targetClassName("jsd.test.pkgB.ClassB")
                            .expectedType(ExpectedClassType.CLASS)
                            .enforceEncapsulation(true)
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("getFriendA").returnType("ClassA").build(),
                                    OopMethodConstraint.builder().name("setFriendA").parameterTypes(List.of("ClassA")).build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.getRules()).hasSize(2);
            assertThat(report.getRules()).allMatch(OopRuleResult::isPassed);
        }
    }

    @Nested
    @DisplayName("Scenario 3: Deep Interface Inheritance & Multiple Interfaces")
    class InterfaceHierarchyTests {

        @Test
        @DisplayName("3.1: Complex multi-interface implementation passes when all direct interfaces are implemented")
        void inspect_MultipleInterfaces_PassesCompliantClass(@TempDir Path classesDir) throws Exception {
            String ifaceCode = """
                    package jsd.test.iface;
                    public interface BaseService { void init(); }
                    """;
            String auditIface = """
                    package jsd.test.iface;
                    public interface Auditable { void logAudit(); }
                    """;
            String serialIface = """
                    package jsd.test.iface;
                    public interface Exportable { byte[] exportData(); }
                    """;
            String implCode = """
                    package jsd.test.iface;
                    public class FullService implements BaseService, Auditable, Exportable {
                        private String serviceName;
                        public FullService() { this.serviceName = "default"; }
                        @Override public void init() {}
                        @Override public void logAudit() {}
                        @Override public byte[] exportData() { return new byte[0]; }
                    }
                    """;

            compileJavaSources(classesDir, Map.of(
                    "jsd/test/iface/BaseService.java", ifaceCode,
                    "jsd/test/iface/Auditable.java", auditIface,
                    "jsd/test/iface/Exportable.java", serialIface,
                    "jsd/test/iface/FullService.java", implCode
            ));

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("MULTI_INTERFACE_CHECK")
                            .targetClassName("jsd.test.iface.FullService")
                            .expectedType(ExpectedClassType.CLASS)
                            .expectedInterfaces(List.of("BaseService", "Auditable", "Exportable"))
                            .enforceEncapsulation(true)
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("3.2: Missing one expected interface out of multiple fails interface verification cleanly")
        void inspect_MissingOneInterface_FailsWithCleanViolation(@TempDir Path classesDir) throws Exception {
            String ifaceA = "package jsd.test.iface; public interface InterfaceA {}";
            String ifaceB = "package jsd.test.iface; public interface InterfaceB {}";
            String impl = "package jsd.test.iface; public class PartialImpl implements InterfaceA {}";

            compileJavaSources(classesDir, Map.of(
                    "jsd/test/iface/InterfaceA.java", ifaceA,
                    "jsd/test/iface/InterfaceB.java", ifaceB,
                    "jsd/test/iface/PartialImpl.java", impl
            ));

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("INTERFACE_CHECK")
                            .targetClassName("jsd.test.iface.PartialImpl")
                            .expectedInterfaces(List.of("InterfaceA", "InterfaceB"))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            assertThat(report.getRules().get(0).getMessage()).contains("InterfaceB");
        }

        @Test
        @DisplayName("3.3: Interface extending another interface verified with expectedType = INTERFACE")
        void inspect_InterfaceInheritance_VerifiesSuperInterface(@TempDir Path classesDir) throws Exception {
            String parentIface = "package jsd.test.iface; public interface ParentInterface { void parentMethod(); }";
            String childIface = "package jsd.test.iface; public interface ChildInterface extends ParentInterface { void childMethod(); }";

            compileJavaSources(classesDir, Map.of(
                    "jsd/test/iface/ParentInterface.java", parentIface,
                    "jsd/test/iface/ChildInterface.java", childIface
            ));

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("CHILD_INTERFACE_CHECK")
                            .targetClassName("jsd.test.iface.ChildInterface")
                            .expectedType(ExpectedClassType.INTERFACE)
                            .expectedInterfaces(List.of("ParentInterface"))
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("childMethod").returnType("void").build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Scenario 4: Obscure Modifiers (native, synchronized, strictfp, volatile, transient)")
    class ObscureModifiersTests {

        @Test
        @DisplayName("4.1: Required obscure modifiers (volatile, transient, synchronized, native) verified successfully")
        void inspect_RequiredObscureModifiers_PassesWhenPresent(@TempDir Path classesDir) throws Exception {
            String code = """
                    package jsd.test.modifiers;
                    public class ObscureClass {
                        private volatile int counter;
                        private transient String cachedToken;

                        public synchronized void syncMethod() {
                            this.counter++;
                        }

                        public native void nativeMethod();
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/modifiers/ObscureClass.java", code);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("OBSCURE_MODIFIERS_RULE")
                            .targetClassName("jsd.test.modifiers.ObscureClass")
                            .fields(List.of(
                                    OopFieldConstraint.builder()
                                            .name("counter")
                                            .type("int")
                                            .requiredModifiers(List.of("private", "volatile"))
                                            .build(),
                                    OopFieldConstraint.builder()
                                            .name("cachedToken")
                                            .type("String")
                                            .requiredModifiers(List.of("private", "transient"))
                                            .build()
                            ))
                            .methods(List.of(
                                    OopMethodConstraint.builder()
                                            .name("syncMethod")
                                            .requiredModifiers(List.of("public", "synchronized"))
                                            .build(),
                                    OopMethodConstraint.builder()
                                            .name("nativeMethod")
                                            .requiredModifiers(List.of("public", "native"))
                                            .build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isTrue();
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("4.2: Forbidden obscure modifiers trigger clean violation reports")
        void inspect_ForbiddenObscureModifiers_FailsWhenPresent(@TempDir Path classesDir) throws Exception {
            String code = """
                    package jsd.test.modifiers;
                    public class ForbiddenModifiersClass {
                        private volatile int counter;
                        public synchronized void dangerousMethod() {}
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/modifiers/ForbiddenModifiersClass.java", code);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("FORBIDDEN_MODIFIERS_RULE")
                            .targetClassName("jsd.test.modifiers.ForbiddenModifiersClass")
                            .fields(List.of(
                                    OopFieldConstraint.builder()
                                            .name("counter")
                                            .forbiddenModifiers(List.of("volatile"))
                                            .build()
                            ))
                            .methods(List.of(
                                    OopMethodConstraint.builder()
                                            .name("dangerousMethod")
                                            .forbiddenModifiers(List.of("synchronized"))
                                            .build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            OopRuleResult result = report.getRules().get(0);
            assertThat(result.getMessage()).contains("forbidden modifier [VOLATILE]");
            assertThat(result.getMessage()).contains("forbidden modifier [SYNCHRONIZED]");
        }

        @Test
        @DisplayName("4.3: Strictfp modifier correctly recognized by ModifierHelper")
        void inspect_StrictfpModifier_ParsedAndEvaluated() {
            int strictMask = ModifierHelper.parseModifier("strictfp");
            assertThat(strictMask).isEqualTo(java.lang.reflect.Modifier.STRICT);

            List<String> violations = ModifierHelper.checkModifiers(
                    0,
                    List.of("strictfp"),
                    null,
                    "Class MathService"
            );
            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).contains("missing required modifier [STRICTFP]");
        }
    }

    @Nested
    @DisplayName("Scenario 5: Subclass Missing Required Abstract Method Implementations")
    class IncompleteSubclassTests {

        @Test
        @DisplayName("5.1: Subclass declared abstract to bypass method implementation fails concrete CLASS requirement and method check")
        void inspect_AbstractSubclassBypassingMethod_FailsExpectedClassType(@TempDir Path classesDir) throws Exception {
            String baseCode = """
                    package jsd.test.worker;
                    public abstract class AbstractWorker {
                        public abstract void executeJob();
                    }
                    """;
            String subCode = """
                    package jsd.test.worker;
                    public abstract class LazyWorker extends AbstractWorker {
                        // Failed to implement executeJob(), so declared abstract!
                    }
                    """;

            compileJavaSources(classesDir, Map.of(
                    "jsd/test/worker/AbstractWorker.java", baseCode,
                    "jsd/test/worker/LazyWorker.java", subCode
            ));

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("CONCRETE_WORKER_CHECK")
                            .targetClassName("jsd.test.worker.LazyWorker")
                            .expectedType(ExpectedClassType.CLASS) // Requires concrete class
                            .expectedSuperclass("AbstractWorker")
                            .methods(List.of(
                                    OopMethodConstraint.builder().name("executeJob").returnType("void").build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            OopRuleResult result = report.getRules().get(0);
            assertThat(result.getMessage()).contains("Expected concrete CLASS, but found ABSTRACT_CLASS");
            assertThat(result.getMessage()).contains("Method 'executeJob()' not found in class LazyWorker");
        }

        @Test
        @DisplayName("5.2: Method implementation with mismatched return type or parameters reports detailed failure")
        void inspect_MethodSignatureMismatch_FailsWithDetailedViolations(@TempDir Path classesDir) throws Exception {
            String code = """
                    package jsd.test.calc;
                    public class Calculator {
                        // Expected int add(int, int) but student wrote double add(double, double)
                        public double add(double a, double b) { return a + b; }
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/calc/Calculator.java", code);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("CALCULATOR_ADD_CHECK")
                            .targetClassName("jsd.test.calc.Calculator")
                            .methods(List.of(
                                    OopMethodConstraint.builder()
                                            .name("add")
                                            .returnType("int")
                                            .parameterTypes(List.of("int", "int"))
                                            .build()
                            ))
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getRules().get(0).getMessage()).contains("Method 'add(int, int)' not found in class Calculator");
        }
    }

    @Nested
    @DisplayName("Scenario 6: Missing Compiled Class (ClassNotFoundException Handling)")
    class MissingClassTests {

        @Test
        @DisplayName("6.1: Non-existent class returns failed rule cleanly with 0 score without throwing")
        void inspect_NonExistentClass_HandledCleanlyAsRuleFailure(@TempDir Path classesDir) {
            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("MISSING_SERVICE_RULE")
                            .targetClassName("jsd.test.missing.NonExistentService")
                            .weight(1.0)
                            .build()
            );

            assertThatNoException().isThrownBy(() -> {
                OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
                assertThat(report.isPassed()).isFalse();
                assertThat(report.getScore()).isEqualTo(0.0);
                assertThat(report.getRules()).hasSize(1);
                OopRuleResult result = report.getRules().get(0);
                assertThat(result.isPassed()).isFalse();
                assertThat(result.getMessage()).contains("was not found in compiled bytecode");
            });
        }

        @Test
        @DisplayName("6.2: Rule with null or blank targetClassName returns clean failure without NullPointerException")
        void inspect_BlankTargetClassName_ReturnsCleanFailure(@TempDir Path classesDir) {
            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("BLANK_TARGET_RULE")
                            .targetClassName("")
                            .weight(1.0)
                            .build(),
                    OopConstraintRule.builder()
                            .ruleName("NULL_TARGET_RULE")
                            .targetClassName(null)
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            assertThat(report.getRules()).hasSize(2);
            assertThat(report.getRules()).allMatch(r -> r.getMessage().contains("Rule missing targetClassName"));
        }
    }

    @Nested
    @DisplayName("Scenario 7: Incompatible Bytecode & Linkage Errors Shielding")
    class LinkageErrorTests {

        @Test
        @DisplayName("7.1: Corrupted .class file (invalid magic number) caught as LinkageError and reported cleanly")
        void inspect_CorruptedClassFile_CatchesLinkageErrorGracefully(@TempDir Path classesDir) throws Exception {
            Path corruptClass = classesDir.resolve("CorruptClass.class");
            // Writing invalid header (0xDEADBEEF instead of 0xCAFEBABE)
            byte[] corruptBytes = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x00, 0x00, 0x00, 0x3D};
            Files.write(corruptClass, corruptBytes);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("CORRUPT_CLASS_CHECK")
                            .targetClassName("CorruptClass")
                            .weight(1.0)
                            .build()
            );

            assertThatNoException().isThrownBy(() -> {
                OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
                assertThat(report.isPassed()).isFalse();
                assertThat(report.getScore()).isEqualTo(0.0);
                assertThat(report.getRules()).hasSize(1);
                OopRuleResult result = report.getRules().get(0);
                assertThat(result.isPassed()).isFalse();
                assertThat(result.getMessage()).contains("Linkage error loading class");
            });
        }

        @Test
        @DisplayName("7.2: Renamed .class file causing NoClassDefFoundError (wrong name) shielded cleanly")
        void inspect_WrongNameInClassFile_ShieldsNoClassDefFoundError(@TempDir Path classesDir) throws Exception {
            String validSource = "public class ActualName {}";
            compileJavaSource(classesDir, "ActualName.java", validSource);

            // Rename ActualName.class to MismatchedName.class
            Path actualClass = classesDir.resolve("ActualName.class");
            Path mismatchedClass = classesDir.resolve("MismatchedName.class");
            Files.move(actualClass, mismatchedClass);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("MISMATCHED_NAME_CHECK")
                            .targetClassName("MismatchedName")
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            OopRuleResult result = report.getRules().get(0);
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessage()).contains("Linkage error loading class");
        }

        @Test
        @DisplayName("7.3: Failure isolation: Corrupted class does NOT prevent valid class from being audited")
        void inspect_MixedCorruptedAndValidClasses_IsolatesFailure(@TempDir Path classesDir) throws Exception {
            // Valid class
            String validCode = "public class ValidClass { public void ping() {} }";
            compileJavaSource(classesDir, "ValidClass.java", validCode);

            // Corrupt class
            Path corruptClass = classesDir.resolve("BrokenClass.class");
            Files.write(corruptClass, new byte[]{0x01, 0x02, 0x03, 0x04});

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("VALID_CLASS_CHECK")
                            .targetClassName("ValidClass")
                            .weight(1.0)
                            .build(),
                    OopConstraintRule.builder()
                            .ruleName("BROKEN_CLASS_CHECK")
                            .targetClassName("BrokenClass")
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            // Exactly 1 of 2 passed -> score = 50.0
            assertThat(report.getScore()).isEqualTo(50.0);
            assertThat(report.getWeightedScore()).isEqualTo(20.0);

            assertThat(report.getRules().get(0).isPassed()).isTrue();
            assertThat(report.getRules().get(1).isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario 8: Extreme Rule Weights & Boundary Rules JSON")
    class ExtremeWeightsAndBoundaryJsonTests {

        @Test
        @DisplayName("8.1: Zero weight rules do not produce division by zero or NaN")
        void inspect_ZeroWeights_DoesNotProduceNaN(@TempDir Path classesDir) throws Exception {
            String code = "public class DummyClass {}";
            compileJavaSource(classesDir, "DummyClass.java", code);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("ZERO_WEIGHT_PASS")
                            .targetClassName("DummyClass")
                            .weight(0.0)
                            .build(),
                    OopConstraintRule.builder()
                            .ruleName("ZERO_WEIGHT_FAIL")
                            .targetClassName("NonExistent")
                            .weight(0.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(Double.isNaN(report.getScore())).isFalse();
            assertThat(Double.isInfinite(report.getScore())).isFalse();
            assertThat(report.getScore()).isEqualTo(100.0);
            assertThat(report.isPassed()).isFalse(); // Because 1 rule failed
        }

        @Test
        @DisplayName("8.2: Very large weights (1e9) handled without overflow")
        void inspect_VeryLargeWeights_CalculatesAccurately(@TempDir Path classesDir) throws Exception {
            String code = "public class BigWeightClass {}";
            compileJavaSource(classesDir, "BigWeightClass.java", code);

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder()
                            .ruleName("HEAVY_PASS")
                            .targetClassName("BigWeightClass")
                            .weight(1_000_000_000.0)
                            .build(),
                    OopConstraintRule.builder()
                            .ruleName("LIGHT_FAIL")
                            .targetClassName("MissingClass")
                            .weight(1.0)
                            .build()
            );

            OopInspectionReport report = inspector.inspect(classesDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            // 1e9 / (1e9 + 1) is ~99.9999999% which rounds to 100.0
            assertThat(report.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("8.3: Empty or null rules JSON strings handled gracefully")
        void inspect_BoundaryRulesJson_HandlesCleanly(@TempDir Path classesDir) throws Exception {
            // Null rules string
            OopInspectionReport nullReport = inspector.inspect(classesDir, (String) null, 0.40);
            assertThat(nullReport.isPassed()).isTrue();
            assertThat(nullReport.getScore()).isEqualTo(100.0);
            assertThat(nullReport.getSummary()).contains("No OOP constraint rules configured");

            // Empty string
            OopInspectionReport emptyReport = inspector.inspect(classesDir, "", 0.40);
            assertThat(emptyReport.isPassed()).isTrue();
            assertThat(emptyReport.getScore()).isEqualTo(100.0);

            // Empty array "[]"
            OopInspectionReport arrayReport = inspector.inspect(classesDir, "[]", 0.40);
            assertThat(arrayReport.isPassed()).isTrue();
            assertThat(arrayReport.getScore()).isEqualTo(100.0);

            // Empty object "{}" -> single rule with missing targetClassName
            OopInspectionReport objReport = inspector.inspect(classesDir, "{}", 0.40);
            assertThat(objReport.isPassed()).isFalse();
            assertThat(objReport.getScore()).isEqualTo(0.0);
            assertThat(objReport.getRules().get(0).getMessage()).contains("Rule missing targetClassName");
        }

        @Test
        @DisplayName("8.4: Non-existent classes directory fails all rules gracefully")
        void inspect_NonExistentClassesDir_FailsAllRulesWithoutCrashing() {
            Path fakeDir = Path.of("target/non_existent_classes_dir_" + System.currentTimeMillis());

            List<OopConstraintRule> rules = List.of(
                    OopConstraintRule.builder().ruleName("R1").targetClassName("Class1").weight(1.0).build(),
                    OopConstraintRule.builder().ruleName("R2").targetClassName("Class2").weight(2.0).build()
            );

            OopInspectionReport report = inspector.inspect(fakeDir, rules, 0.40);
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getScore()).isEqualTo(0.0);
            assertThat(report.getRules()).hasSize(2);
            assertThat(report.getRules()).allMatch(r -> r.getMessage().contains("Classes directory not found"));
        }
    }

    @Nested
    @DisplayName("Scenario 9: OopJUnit5TestRunner Adversarial Edge Cases")
    class JUnit5RunnerAdversarialTests {

        @Test
        @DisplayName("9.1: Test method with infinite loop terminates within configured timeout")
        void runTests_InfiniteLoop_TerminatesByTimeout(@TempDir Path classesDir) throws Exception {
            String testCode = """
                    package jsd.test.hanging;
                    import org.junit.jupiter.api.Test;
                    import org.junit.jupiter.api.Timeout;
                    import java.util.concurrent.TimeUnit;

                    public class HangingTest {
                        @Test
                        void testHangingLoop() throws InterruptedException {
                            // Infinite sleep/busy loop
                            Thread.sleep(10000);
                        }
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/hanging/HangingTest.java", testCode);

            long start = System.currentTimeMillis();
            ExecutionReport.UnitTestPhase phase = testRunner.runTests(
                    classesDir,
                    classesDir,
                    List.of("jsd.test.hanging.HangingTest"),
                    1, // 1 second timeout
                    0.60
            );
            long elapsed = System.currentTimeMillis() - start;

            // Must terminate reasonably close to timeout (under 4000ms), NOT 10000ms
            assertThat(elapsed).as("Execution should terminate quickly due to timeout").isLessThan(5000);
            assertThat(phase.getPassedCount()).isEqualTo(0);
            assertThat(phase.getTotalCount()).isEqualTo(1);
            assertThat(phase.getScore()).isEqualTo(0.0);
            assertThat(phase.getFailures()).hasSize(1);
            assertThat(phase.getFailures().get(0).getError()).contains("TimeoutException");
        }

        @Test
        @DisplayName("9.2: Test throwing unhandled RuntimeException recorded cleanly in failure breakdown")
        void runTests_ThrowsRuntimeException_RecordsCleanFailure(@TempDir Path classesDir) throws Exception {
            String testCode = """
                    package jsd.test.err;
                    import org.junit.jupiter.api.Test;

                    public class ExplodingTest {
                        @Test
                        void testCrash() {
                            throw new IllegalStateException("CRITICAL_INTERNAL_TEST_CRASH");
                        }
                    }
                    """;
            compileJavaSource(classesDir, "jsd/test/err/ExplodingTest.java", testCode);

            ExecutionReport.UnitTestPhase phase = testRunner.runTests(
                    classesDir,
                    classesDir,
                    List.of("jsd.test.err.ExplodingTest"),
                    2,
                    0.60
            );

            assertThat(phase.getPassedCount()).isEqualTo(0);
            assertThat(phase.getTotalCount()).isEqualTo(1);
            assertThat(phase.getScore()).isEqualTo(0.0);
            assertThat(phase.getFailures()).hasSize(1);
            assertThat(phase.getFailures().get(0).getError()).contains("IllegalStateException: CRITICAL_INTERNAL_TEST_CRASH");
        }

        @Test
        @DisplayName("9.3: Missing test class reported without crashing test runner")
        void runTests_MissingTestClass_HandledCleanly(@TempDir Path classesDir) {
            ExecutionReport.UnitTestPhase phase = testRunner.runTests(
                    classesDir,
                    classesDir,
                    List.of("com.missing.test.GhostTest"),
                    2,
                    0.60
            );

            // Test runner registers selector by name; platform fails discovery or reports 0 passed
            assertThat(phase.getPassedCount()).isEqualTo(0);
            assertThat(phase.getScore()).isEqualTo(0.0);
        }
    }
}
