package jsd.toiyeujava.mini_leetcode.judge.authoring;

import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceRunServiceTest {

    private static final String DOUBLER = """
            import java.util.Scanner;
            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    System.out.println(in.nextInt() * 2);
                }
            }
            """;

    /** Executor giả: "chạy" chương trình bằng cách nhân đôi số trong stdin; ghi lại lớp main + workDir. */
    private static final class FakeExecutor extends OopContainerExecutor {
        final List<String> mainClasses = new ArrayList<>();
        final List<Boolean> classesExisted = new ArrayList<>();

        FakeExecutor() {
            super(pb -> {
                throw new UnsupportedOperationException();
            }, new SandboxHostPathResolver());
        }

        @Override
        public SandboxResult runClass(Path workDir, String className, List<String> args, String input,
                                      long timeoutMs, int memoryLimitMb) {
            mainClasses.add(className);
            classesExisted.add(Files.exists(workDir.resolve("classes").resolve("Main.class")));
            if (input.isBlank()) {
                return new SandboxResult(50, 1000, 1, "", "NoSuchElementException", false, false, false, true, null);
            }
            String out = (Integer.parseInt(input.trim()) * 2) + "\n";
            return new SandboxResult(120, 20_000, 0, out, "", false, false, false, true, null);
        }
    }

    private final FakeExecutor executor = new FakeExecutor();
    private final ReferenceRunService service =
            new ReferenceRunService(new OopGrindingCompiler(), new MultiFileSourceExtractor(), executor);

    @Test
    void runsEveryInputAndComparesWithJudgeComparator() {
        var result = service.run(List.of(new ReferenceRunService.SourceFile("Main.java", DOUBLER)), List.of(
                new ReferenceRunService.CaseInput("21\n", "42  \n\n"), // khoảng trắng cuối bị bỏ qua như khi chấm
                new ReferenceRunService.CaseInput("5\n", "11\n"),
                new ReferenceRunService.CaseInput("7\n", null),
                new ReferenceRunService.CaseInput("", "x")), 1000, 256);

        assertThat(result.compiled()).isTrue();
        assertThat(result.mainClass()).isEqualTo("Main");
        assertThat(result.error()).isNull();
        assertThat(result.cases()).extracting(ReferenceRunService.CaseRun::matchesExpected)
                .containsExactly(true, false, null, false);
        assertThat(result.cases().get(1).stdout()).isEqualTo("10\n");
        assertThat(result.cases().get(3).succeeded()).isFalse();
        assertThat(executor.mainClasses).containsOnly("Main").hasSize(4);
        assertThat(executor.classesExisted).containsOnly(true);
    }

    @Test
    void reportsCompileErrorsWithoutTempPrefixAndRunsNothing() {
        var result = service.run(List.of(new ReferenceRunService.SourceFile("Main.java", "public class Main { int x = ; }")),
                List.of(new ReferenceRunService.CaseInput("1", null)), 1000, 256);
        assertThat(result.compiled()).isFalse();
        assertThat(result.compileOutput()).contains("Main.java").doesNotContain("src/Main.java");
        assertThat(executor.mainClasses).isEmpty();
    }

    @Test
    void requiresAMainMethod() {
        var result = service.run(List.of(new ReferenceRunService.SourceFile("Account.java", "public class Account {}")),
                List.of(new ReferenceRunService.CaseInput("1", null)), 1000, 256);
        assertThat(result.compiled()).isFalse();
        assertThat(result.compileOutput()).contains("public static void main");
    }

    @Test
    void rejectsEmptyAndOversizedRequests() {
        var files = List.of(new ReferenceRunService.SourceFile("Main.java", DOUBLER));
        assertThatThrownBy(() -> service.run(List.of(), List.of(new ReferenceRunService.CaseInput("1", null)), 1000, 256))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.run(files, List.of(), 1000, 256)).isInstanceOf(IllegalArgumentException.class);
        var tooMany = new ArrayList<ReferenceRunService.CaseInput>();
        for (int i = 0; i <= ReferenceRunService.MAX_INPUTS; i++) {
            tooMany.add(new ReferenceRunService.CaseInput("1", null));
        }
        assertThatThrownBy(() -> service.run(files, tooMany, 1000, 256)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cleansUpItsWorkspace() throws Exception {
        Path root = Path.of(SandboxHostPathResolver.CONSOLE_ROOT_NAME);
        service.run(List.of(new ReferenceRunService.SourceFile("Main.java", DOUBLER)),
                List.of(new ReferenceRunService.CaseInput("1", null)), 1000, 256);
        try (var dirs = Files.list(root)) {
            assertThat(dirs.filter(p -> p.getFileName().toString().startsWith("ref-"))).isEmpty();
        }
    }

    /**
     * Chạy thật trong Docker sandbox (image JDK 17). Chỉ chạy khi có Docker VÀ JVM chạy test là 17 —
     * javac in-process sinh bytecode theo JDK của judge, như image judge thật (cũng JDK 17).
     */
    @Test
    @EnabledIf("dockerAvailable")
    void runsInRealDockerSandbox() {
        var real = new ReferenceRunService(new OopGrindingCompiler(), new MultiFileSourceExtractor(),
                new OopContainerExecutor(new SandboxHostPathResolver()));
        var result = real.run(List.of(new ReferenceRunService.SourceFile("Main.java", DOUBLER)),
                List.of(new ReferenceRunService.CaseInput("21\n", "42\n")), 10_000, 256);
        assertThat(result.error()).isNull();
        assertThat(result.cases()).hasSize(1);
        var c = result.cases().get(0);
        assertThat(c.stdout()).withFailMessage("exit=%s stderr=%s", c.exitCode(), c.stderr()).isEqualTo("42\n");
        assertThat(c.matchesExpected()).isTrue();
    }

    static boolean dockerAvailable() {
        if (Runtime.version().feature() != 17) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
