package jsd.toiyeujava.mini_leetcode.judge.console;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sinh harness REPL để đánh giá biểu thức Java của học viên khi bài OOP
 * KHÔNG có {@code public static void main(String[])}.
 *
 * <p>Ý tưởng: harness sinh ra 1 file Java (compile trong sandbox) chứa một lớp
 * {@code __ConsoleProbe} với {@code main} đọc biểu thức từ {@code System.in}
 * rồi đánh giá nó bằng reflection:
 *
 * <ul>
 *   <li>biểu thức có dạng {@code new Circle("c", 2).getArea()} → dùng
 *       {@code Class.forName} + {@code getDeclaredConstructor} + {@code invoke};</li>
 *   <li>biểu thức là một tên biến/kiểu đơn giản → dùng {@code ScriptEngine}
 *       (Nashorn đã bị bỏ ở JDK 15+) nên không dùng; thay vào đó dùng
 *       {@code javax.tools.JavaCompiler} để compile biểu thức thành một lớp
 *       tạm thời rồi chạy nó.</li>
 * </ul>
 *
 * <p>Để giữ đơn giản và an toàn, harness dùng {@code JavaCompiler} để biên dịch
 * biểu thức người dùng gõ thành một lớp Java tạm thời
 * ({@code __ExprEval}) rồi {@code invoke} — biểu thức được đánh giá đúng ngữ
 * nghĩa Java, và mọi thứ vẫn chạy trong container sandbox.
 */
@Slf4j
public final class ConsoleReplHarness {

    public static final String PROBE_CLASS_NAME = "__ConsoleProbe";
    public static final String PROBE_SOURCE_FILE = "__ConsoleProbe.java";

    /** Biểu thức mặc định khi người dùng bấm "Chạy mẫu" mà chưa nhập gì. */
    public static final String DEFAULT_EXPRESSION = "new Object()";

    private ConsoleReplHarness() {
    }

    /**
     * Sinh source cho lớp probe REPL.
     *
     * @param studentClasses danh sách FQCN của lớp học viên (dùng để import tĩnh khi cần)
     */
    public static String buildProbeSource() {
        return """
                import java.io.BufferedReader;
                import java.io.InputStreamReader;
                import java.io.PrintWriter;
                import java.nio.charset.StandardCharsets;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.List;

                /**
                 * REPL console cho bài OOP: đọc biểu thức từ stdin, biên dịch và đánh giá,
                 * in kết quả ra stdout.
                 *
                 * <p>Lớp này được SINH TỰ ĐỘNG — không dùng để chấm điểm.
                 */
                public final class __ConsoleProbe {

                    private __ConsoleProbe() {
                    }

                    public static void main(String[] args) throws Exception {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(System.in, StandardCharsets.UTF_8));
                        PrintWriter out = new PrintWriter(System.out, true);

                        String line = reader.readLine();
                        while (line != null) {
                            String expression = line.trim();
                            if (!expression.isEmpty()) {
                                try {
                                    String result = evaluate(expression);
                                    if (result != null) {
                                        out.println(result);
                                    }
                                } catch (Throwable t) {
                                    Throwable root = unwrap(t);
                                    out.println("__CONSOLE_ERROR__=" + root.getClass().getName()
                                            + ": " + String.valueOf(root.getMessage()));
                                }
                            }
                            out.println("__CONSOLE_PROMPT__");
                            out.flush();
                            line = reader.readLine();
                        }
                    }

                    private static Throwable unwrap(Throwable t) {
                        if (t instanceof java.lang.reflect.InvocationTargetException ite
                                && ite.getCause() != null) {
                            return ite.getCause();
                        }
                        return t;
                    }

                    /**
                     * Biên dịch biểu thức thành một lớp tạm rồi chạy để lấy giá trị.
                     *
                     * <p>Lớp {@code __ExprEval} in kết quả ra stdout và trả về
                     * {@code null}; giá trị đã in sẵn nên probe KHÔNG in thêm.
                     */
                    private static String evaluate(String expression) throws Exception {
                        Path work = Files.createTempDirectory("console-eval");
                        Path source = work.resolve("__ExprEval.java");
                        Files.writeString(source, buildEvalSource(expression), StandardCharsets.UTF_8);

                        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
                        if (compiler == null) {
                            throw new IllegalStateException("No Java compiler available inside the sandbox");
                        }
                        int rc = compiler.run(null, null, null,
                                "-d", work.toString(), source.toString());
                        if (rc != 0) {
                            throw new IllegalStateException("Cannot evaluate expression: " + expression);
                        }

                        Class<?> clazz;
                        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                                new java.net.URL[]{work.toUri().toURL()},
                                __ConsoleProbe.class.getClassLoader())) {
                            clazz = Class.forName("__ExprEval", true, loader);
                        }
                        java.lang.reflect.Method main = clazz.getDeclaredMethod("main", String[].class);
                        main.setAccessible(true);
                        main.invoke(null, (Object) new String[0]);
                        return null;
                    }

                    /**
                     * Sinh source lớp tạm để đánh giá biểu thức.
                     */
                    private static String buildEvalSource(String expression) {
                        return "public final class __ExprEval {\\n"
                                + "    public static void main(String[] args) {\\n"
                                + "        Object __result = " + expression + ";\\n"
                                + "        System.out.println(__result);\\n"
                                + "    }\\n"
                                + "}\\n";
                    }
                }
                """;
    }

    /**
     * Ghi source probe vào thư mục làm việc để biên dịch cùng với mã nguồn học viên.
     */
    public static boolean writeProbeSource(Path workDir) {
        try {
            Files.writeString(workDir.resolve(PROBE_SOURCE_FILE), buildProbeSource(), java.nio.charset.StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.error("Failed to write console probe source into {}", workDir, e);
            return false;
        }
    }

    /** Danh sách tên lớp học viên (để hiển thị gợi ý). */
    public static List<String> listStudentClasses(Path classesDir) {
        List<String> names = new ArrayList<>();
        try (var stream = Files.walk(classesDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> {
                        String rel = classesDir.relativize(p).toString()
                                .replace('\\', '.')
                                .replace('/', '.');
                        names.add(rel.substring(0, rel.length() - ".class".length()));
                    });
        } catch (Exception ignored) {
        }
        return names;
    }
}
