package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OopGrindingCompilerStressTest {

    private OopGrindingCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new OopGrindingCompiler();
    }

    @AfterEach
    void tearDown() {
        compiler.destroy();
    }

    @Test
    @DisplayName("Stress 1: Circular class dependencies across packages compile successfully in one batch")
    void compile_CircularClassDependenciesAcrossPackages_CompilesSuccessfullyInBatch(@TempDir Path workDir) throws IOException {
        Path orderDir = workDir.resolve("com/company/order");
        Path customerDir = workDir.resolve("com/company/customer");
        Path billingDir = workDir.resolve("com/company/billing");
        Files.createDirectories(orderDir);
        Files.createDirectories(customerDir);
        Files.createDirectories(billingDir);

        // Order references Customer and Invoice
        Files.writeString(orderDir.resolve("Order.java"), """
            package com.company.order;
            import com.company.customer.Customer;
            import com.company.billing.Invoice;
            public class Order {
                private final String orderId;
                private final Customer customer;
                private Invoice invoice;
                public Order(String orderId, Customer customer) {
                    this.orderId = orderId;
                    this.customer = customer;
                }
                public String getOrderId() { return orderId; }
                public Customer getCustomer() { return customer; }
                public Invoice getInvoice() { return invoice; }
                public void setInvoice(Invoice invoice) { this.invoice = invoice; }
            }
            """);

        // Customer references Order
        Files.writeString(customerDir.resolve("Customer.java"), """
            package com.company.customer;
            import com.company.order.Order;
            import java.util.ArrayList;
            import java.util.List;
            public class Customer {
                private final String customerId;
                private final List<Order> orders = new ArrayList<>();
                public Customer(String customerId) {
                    this.customerId = customerId;
                }
                public String getCustomerId() { return customerId; }
                public void addOrder(Order order) { orders.add(order); }
                public List<Order> getOrders() { return orders; }
            }
            """);

        // Invoice references Order and Customer (3-way cyclic dependency graph)
        Files.writeString(billingDir.resolve("Invoice.java"), """
            package com.company.billing;
            import com.company.order.Order;
            import com.company.customer.Customer;
            public class Invoice {
                private final Order order;
                private final Customer customer;
                public Invoice(Order order) {
                    this.order = order;
                    this.customer = order.getCustomer();
                }
                public Order getOrder() { return order; }
                public Customer getCustomer() { return customer; }
            }
            """);

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success())
                .withFailMessage("Batch compilation failed: %s", result.output())
                .isTrue();
        assertThat(workDir.resolve("classes/com/company/order/Order.class")).exists();
        assertThat(workDir.resolve("classes/com/company/customer/Customer.class")).exists();
        assertThat(workDir.resolve("classes/com/company/billing/Invoice.class")).exists();
    }

    @Test
    @DisplayName("Stress 2: Triangular circular dependency across 3 packages compiles in one batch")
    void compile_TriangularCrossPackageCycle_CompilesSuccessfully(@TempDir Path workDir) throws IOException {
        Path pkgA = workDir.resolve("pkgA");
        Path pkgB = workDir.resolve("pkgB");
        Path pkgC = workDir.resolve("pkgC");
        Files.createDirectories(pkgA);
        Files.createDirectories(pkgB);
        Files.createDirectories(pkgC);

        Files.writeString(pkgA.resolve("NodeA.java"), """
            package pkgA;
            import pkgB.NodeB;
            public class NodeA {
                public NodeB makeB() { return new NodeB(); }
            }
            """);

        Files.writeString(pkgB.resolve("NodeB.java"), """
            package pkgB;
            import pkgC.NodeC;
            public class NodeB {
                public NodeC makeC() { return new NodeC(); }
            }
            """);

        Files.writeString(pkgC.resolve("NodeC.java"), """
            package pkgC;
            import pkgA.NodeA;
            public class NodeC {
                public NodeA makeA() { return new NodeA(); }
            }
            """);

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isTrue();
        assertThat(workDir.resolve("classes/pkgA/NodeA.class")).exists();
        assertThat(workDir.resolve("classes/pkgB/NodeB.class")).exists();
        assertThat(workDir.resolve("classes/pkgC/NodeC.class")).exists();
    }

    @Test
    @DisplayName("Stress 3: Annotation processor & static block suppression (-proc:none security)")
    void compile_AnnotationProcessorAndStaticBlock_ProcNonePreventsProcessorExecution(@TempDir Path workDir) throws IOException {
        Path markerFile = Path.of("target/adversarial_marker_" + System.nanoTime() + ".txt");
        Files.deleteIfExists(markerFile);

        Path evilDir = workDir.resolve("evil");
        Path servicesDir = workDir.resolve("META-INF/services");
        Files.createDirectories(evilDir);
        Files.createDirectories(servicesDir);

        // Annotation
        Files.writeString(evilDir.resolve("AdversarialAnno.java"), """
            package evil;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.SOURCE)
            public @interface AdversarialAnno {}
            """);

        // Malicious student class attempting to execute code on host via static initializer or annotation processor registration
        String markerPathEscaped = markerFile.toAbsolutePath().toString().replace("\\", "\\\\");
        Files.writeString(evilDir.resolve("MaliciousHostExploit.java"), """
            package evil;
            import java.nio.file.Files;
            import java.nio.file.Path;
            public class MaliciousHostExploit {
                static {
                    try {
                        Files.writeString(Path.of("%s"), "STATIC_PWNED");
                    } catch (Exception ignored) {}
                }
                public int compute() { return 42; }
            }
            """.formatted(markerPathEscaped));

        // Register in META-INF/services (if annotation processing was active, javac would load this class)
        Files.writeString(servicesDir.resolve("javax.annotation.processing.Processor"), "evil.MaliciousHostExploit\n");

        // User code using annotation and malicious static block attempting host compromise
        Files.writeString(evilDir.resolve("TargetCode.java"), """
            package evil;
            import java.nio.file.Files;
            import java.nio.file.Path;
            @AdversarialAnno
            public class TargetCode {
                static {
                    try {
                        Files.writeString(Path.of("%s"), "TARGET_STATIC_PWNED");
                    } catch (Exception ignored) {}
                }
                public int compute() { return 42; }
            }
            """.formatted(markerPathEscaped));

        CompileResult result = compiler.compile(workDir);

        // Compilation must succeed without running processor or static initializers
        assertThat(result.success())
                .withFailMessage("Compilation failed: %s", result.output())
                .isTrue();
        assertThat(workDir.resolve("classes/evil/TargetCode.class")).exists();
        assertThat(markerFile)
                .withFailMessage("Host security breached! Marker file was created during compilation.")
                .doesNotExist();
    }

    @Test
    @DisplayName("Stress 4: Missing imports return false with symbol diagnostics")
    void compile_MissingImports_ReturnsFalseWithSymbolDiagnostics(@TempDir Path workDir) throws IOException {
        Path code = workDir.resolve("BrokenImport.java");
        Files.writeString(code, """
            package com.example;
            import com.nonexistent.pki.SecretService;
            public class BrokenImport {
                private SecretService service;
            }
            """);

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("error:");
        assertThat(result.output()).contains("BrokenImport.java");
        assertThat(result.output()).containsAnyOf("does not exist", "cannot find symbol");
    }

    @Test
    @DisplayName("Stress 5: Unclosed braces return false with parser diagnostics")
    void compile_UnclosedBrace_ReturnsFalseWithParserDiagnostics(@TempDir Path workDir) throws IOException {
        Path code = workDir.resolve("Unclosed.java");
        Files.writeString(code, """
            public class Unclosed {
                public void doSomething() {
                    int x = 10;
            """);

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("error:");
        assertThat(result.output()).contains("Unclosed.java");
        assertThat(result.output()).containsAnyOf("reached end of file while parsing", "expected");
    }

    @Test
    @DisplayName("Stress 6: Diagnostic output with massive errors is strictly capped <= 4016 chars with truncation suffix")
    void compile_MassiveErrors_CapsDiagnosticsUnder4016Chars(@TempDir Path workDir) throws IOException {
        // Generate deep package hierarchy with long names to exceed 4000 characters
        Path pkg = workDir.resolve("com/enterprise/deeply/nested/architecture/subsystem");
        Files.createDirectories(pkg);
        for (int i = 0; i < 25; i++) {
            Path file = pkg.resolve("VeryLongDescriptiveClassNameNumber" + i + ".java");
            StringBuilder sb = new StringBuilder();
            sb.append("package com.enterprise.deeply.nested.architecture.subsystem;\n");
            sb.append("public class VeryLongDescriptiveClassNameNumber").append(i).append(" {\n");
            for (int j = 0; j < 5; j++) {
                sb.append("  nonexistentReturnType").append(j)
                  .append(" variableWithVeryLongDescriptiveNameForTesting")
                  .append(j).append(" = ;\n");
            }
            sb.append("}\n");
            Files.writeString(file, sb.toString());
        }

        CompileResult result = compiler.compile(workDir);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).endsWith("... [truncated]");
        assertThat(result.output().length()).isLessThanOrEqualTo(4016);
    }

    @Test
    @DisplayName("Stress 7: Timeout budget aborts slow compilation and returns timeout error")
    void compile_TimeoutBudget_AbortsSlowCompilation(@TempDir Path workDir) throws IOException {
        // Create 20 files
        for (int i = 0; i < 20; i++) {
            Files.writeString(workDir.resolve("Class" + i + ".java"),
                    "public class Class" + i + " { int x = " + i + "; }");
        }

        // Extremely low timeout to force timeout handler
        CompileResult result = compiler.compile(workDir, null, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).containsAnyOf("timed out after 1ms", "interrupted");
    }
}
