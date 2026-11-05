package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử việc phát hiện {@code public static void main(String[])} bằng bytecode.
 *
 * <p>Regression test cho lỗi đọc constant pool: {@code CONSTANT_Methodref} /
 * {@code Fieldref} / {@code InterfaceMethodref} chỉ có HAI {@code u2} theo JVMS §4.4.
 * Khi đọc ba {@code u2} thì offset của toàn bộ constant pool còn lại lệch ⇒ mọi class
 * chứa lời gọi hàm đều không parse được ⇒ {@code detectMainClass} luôn rỗng.
 */
class OopMainClassDetectorTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------ happy path

    @Test
    @DisplayName("Class with main + method references → main class is detected")
    void detectMainClass_ClassWithMethodCalls_ReturnsMainClass() throws IOException {
        Path classes = compile(
                "Main.java",
                """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(new java.util.ArrayList<String>().size());
                    }
                }
                """);

        Optional<String> detected = OopMainClassDetector.detectMainClass(classes);

        assertThat(detected).contains("Main");
    }

    @Test
    @DisplayName("Class with main + fields + interfaces → main class is detected")
    void detectMainClass_ClassWithFieldsAndInterfaces_ReturnsMainClass() throws IOException {
        Path classes = compile(
                "Runner.java",
                """
                public class Runner implements Runnable {
                    private static final String NAME = "runner";

                    public static void main(String[] args) {
                        new Runner().run();
                    }

                    @Override
                    public void run() {
                        System.out.println(NAME);
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).contains("Runner");
    }

    @Test
    @DisplayName("Main in a packaged class → fully qualified name is returned")
    void detectMainClass_PackagedClass_ReturnsFullyQualifiedName() throws IOException {
        Path classes = compile(
                "app/Launcher.java",
                """
                package app;

                public class Launcher {
                    public static void main(String[] args) {
                        System.out.println("launch");
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).contains("app.Launcher");
    }

    // ------------------------------------------------------------------ negative path

    @Test
    @DisplayName("Class without main → empty")
    void detectMainClass_NoMain_ReturnsEmpty() throws IOException {
        Path classes = compile(
                "Circle.java",
                """
                public class Circle {
                    private final double radius;

                    public Circle(double radius) {
                        this.radius = radius;
                    }

                    public double getArea() {
                        return Math.PI * radius * radius;
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).isEmpty();
    }

    @Test
    @DisplayName("main that is not public static → empty")
    void detectMainClass_NonPublicStaticMain_ReturnsEmpty() throws IOException {
        Path classes = compile(
                "Hidden.java",
                """
                public class Hidden {
                    static void main(String[] args) {
                        System.out.println("hidden");
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).isEmpty();
    }

    @Test
    @DisplayName("main with a different signature → empty")
    void detectMainClass_WrongMainSignature_ReturnsEmpty() throws IOException {
        Path classes = compile(
                "Other.java",
                """
                public class Other {
                    public static int main(int value) {
                        return value;
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).isEmpty();
    }

    @Test
    @DisplayName("Empty / missing classes directory → empty, no exception")
    void detectMainClass_MissingDirectory_ReturnsEmpty() {
        assertThat(OopMainClassDetector.detectMainClass(tempDir.resolve("nope"))).isEmpty();
    }

    @Test
    @DisplayName("Generated harness classes are never picked as the student's main")
    void detectMainClass_GeneratedHarness_IsIgnored() throws IOException {
        Path classes = compile(
                "__ConsoleProbe.java",
                """
                public final class __ConsoleProbe {
                    public static void main(String[] args) {
                        System.out.println("probe");
                    }
                }
                """);

        assertThat(OopMainClassDetector.detectMainClass(classes)).isEmpty();
    }

    @Test
    @DisplayName("Student main wins even when the harness sorts first")
    void detectMainClass_StudentMainAlongsideHarness_ReturnsStudentMain() throws IOException {
        Path classes = compile(
                "__ConsoleProbe.java",
                """
                public final class __ConsoleProbe {
                    public static void main(String[] args) {
                        System.out.println("probe");
                    }
                }
                """);
        Path student = classes.resolve("solution");
        Files.createDirectories(student);
        Path source = tempDir.resolve("src").resolve("solution").resolve("Solver.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package solution;

                public class Solver {
                    public static void main(String[] args) {
                        System.out.println("solver");
                    }
                }
                """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> args = new java.util.ArrayList<>(List.of(
                "-d", classes.toAbsolutePath().toString(), "-encoding", "UTF-8", "-proc:none"));
        args.add(source.toAbsolutePath().toString());
        assertThat(compiler.run(null, null, null, args.toArray(new String[0]))).isZero();

        assertThat(OopMainClassDetector.detectMainClass(classes)).contains("solution.Solver");
    }

    @Test
    @DisplayName("Garbage class file → empty, no exception")
    void detectMainClass_GarbageFile_ReturnsEmpty() throws IOException {
        Path classes = Files.createDirectories(tempDir.resolve("garbage"));
        Files.writeString(classes.resolve("Broken.class"), "not a class file");

        assertThat(OopMainClassDetector.detectMainClass(classes)).isEmpty();
    }

    @Test
    @DisplayName("Two student classes with main → both reported (console asks the user to keep one)")
    void detectMainClasses_TwoMains_ReturnsBoth() throws IOException {
        compile("Animal.java", "public class Animal { public static void main(String[] a) {} }");
        Path classes = compile("Main.java", "public class Main { public static void main(String[] a) {} }");

        assertThat(OopMainClassDetector.detectMainClasses(classes)).containsExactly("Animal", "Main");
    }

    // ------------------------------------------------------------------ helpers

    /** Biên dịch source Java rồi trả về thư mục chứa {@code .class}. */
    private Path compile(String relativePath, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK with javac is required for this test").isNotNull();

        Path srcDir = Files.createDirectories(tempDir.resolve("src"));
        Path sourceFile = srcDir.resolve(relativePath);
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        List<String> options = List.of(
                "-d", classesDir.toAbsolutePath().toString(),
                "-encoding", "UTF-8",
                "-proc:none");
        List<String> args = new java.util.ArrayList<>(options);
        args.add(sourceFile.toAbsolutePath().toString());
        int rc = compiler.run(null, null, null, args.toArray(new String[0]));
        assertThat(rc).as("compilation of " + relativePath + " must succeed").isZero();
        return classesDir;
    }
}
