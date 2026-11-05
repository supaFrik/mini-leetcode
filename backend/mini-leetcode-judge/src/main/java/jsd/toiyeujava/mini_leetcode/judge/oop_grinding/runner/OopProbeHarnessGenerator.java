package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sinh và biên dịch một lớp probe dùng để đo số liệu của mã nguồn OOP.
 *
 * <p>Probe chạy TRONG container sandbox và gọi {@code main} của lớp học viên
 * (nếu có) bằng reflection — nhờ vậy mọi code học viên chạy đều nằm trong
 * container, không chạm vào JVM của judge worker.
 *
 * <p>Khi lớp học viên KHÔNG có {@code main} (bài OOP chỉ yêu cầu thiết kế lớp),
 * probe nạp tất cả class của học viên (không khởi tạo, nên không chạy side
 * effect) để số đo phản ánh việc nạp bytecode của lớp học viên.
 */
@Slf4j
@Component
public class OopProbeHarnessGenerator {

    /** Tên lớp probe (không có package) — chạy bằng {@code java -cp classes __OopMetricsProbe}. */
    public static final String PROBE_CLASS_NAME = "__OopMetricsProbe";
    public static final String PROBE_SOURCE_FILE = "__OopMetricsProbe.java";

    private final OopGrindingCompiler compiler;

    @Autowired
    public OopProbeHarnessGenerator(OopGrindingCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * Sinh source probe.
     *
     * @param targetClass lớp học viên cần gọi {@code main}; null/blank ⇒ chỉ nạp class
     */
    public String buildProbeSource(String targetClass) {
        String target = targetClass == null ? "" : targetClass.trim();
        // Tên lớp được nhúng thẳng vào source (dưới dạng string literal) thay vì
        // truyền qua args: `measure()` không truyền tham số cho container, nên nếu
        // đọc từ args thì probe sẽ KHÔNG BAO GIỜ gọi main của học viên ⇒ số đo
        // runtime/peak-memory chỉ phản ánh việc nạp class, không phải chương trình chạy.
        String targetLiteral = javaLiteral(target);
        return """
                import java.io.File;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.List;

                /**
                 * Probe đo số liệu (wall-clock + peak memory của cgroup) cho mã nguồn OOP.
                 * Sinh tự động — không dùng để chấm điểm.
                 */
                public final class %PROBE_NAME% {

                    /** Lớp học viên cần gọi main (rỗng nếu học viên không viết main). */
                    private static final String TARGET_CLASS = "%TARGET_CLASS%";

                    private %PROBE_NAME%() {
                    }

                    public static void main(String[] args) {
                        String target = TARGET_CLASS;
                        if (target.isEmpty() && args.length > 0 && args[0] != null) {
                            target = args[0].trim();
                        }
                        if (!target.isEmpty()) {
                            runMain(target);
                            return;
                        }
                        loadStudentClasses();
                    }

                    private static void runMain(String target) {
                        try {
                            Class<?> clazz = Class.forName(target);
                            java.lang.reflect.Method main = clazz.getDeclaredMethod("main", String[].class);
                            main.setAccessible(true);
                            main.invoke(null, (Object) new String[0]);
                        } catch (Throwable t) {
                            System.out.println("__PROBE_MAIN_ERROR__=" + t.getClass().getName() + ": " + t.getMessage());
                        }
                    }

                    private static void loadStudentClasses() {
                        Path root = Path.of("/workspace/classes");
                        if (!Files.isDirectory(root)) {
                            System.out.println("__PROBE_LOADED_CLASSES__=0");
                            return;
                        }
                        List<Path> classFiles = new ArrayList<>();
                        try (var stream = Files.walk(root)) {
                            stream.filter(p -> p.getFileName().toString().endsWith(".class")).forEach(classFiles::add);
                        } catch (Exception ignored) {
                        }
                        int loaded = 0;
                        for (Path p : classFiles) {
                            String rel = root.relativize(p).toString().replace(File.separatorChar, '.').replace('/', '.');
                            String name = rel.substring(0, rel.length() - ".class".length());
                            if (name.contains("$") || name.startsWith("%PROBE_NAME%")) {
                                continue;
                            }
                            try {
                                Class.forName(name, false, %PROBE_NAME%.class.getClassLoader());
                                loaded++;
                            } catch (Throwable ignored) {
                            }
                        }
                        System.out.println("__PROBE_LOADED_CLASSES__=" + loaded);
                    }
                }
                """
                .replace("%PROBE_NAME%", PROBE_CLASS_NAME)
                .replace("%TARGET_CLASS%", targetLiteral);
    }

    /** Chuyển chuỗi thành Java string literal (escape backslash, quote và newline). */
    private static String javaLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
    }

    /**
     * Sinh source probe và biên dịch vào thư mục {@code classes} của {@code workDir}.
     *
     * @return {@code true} nếu biên dịch thành công
     */
    public boolean generateAndCompile(Path workDir, String targetClass) {
        try {
            Path source = workDir.resolve(PROBE_SOURCE_FILE);
            Files.writeString(source, buildProbeSource(targetClass));
            List<Path> sources = List.of(source);
            CompileResult result = compiler.compile(workDir, sources);
            if (!result.success()) {
                log.warn("Failed to compile OOP metrics probe: {}", result.output());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to generate OOP metrics probe in {}", workDir, e);
            return false;
        }
    }
}
