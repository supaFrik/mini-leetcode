package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử probe đo số liệu cho bài OOP.
 *
 * <p>Regression test: probe phải nhúng tên lớp học viên vào source. Trước đây
 * {@code %TARGET_CLASS%} không tồn tại trong template và {@code measure()} không
 * truyền args cho container ⇒ probe KHÔNG BAO GIỜ gọi {@code main} của học viên ⇒
 * runtime/peak-memory chỉ phản ánh việc nạp class, không phải chương trình chạy.
 */
class OopProbeHarnessGeneratorTest {

    private final OopProbeHarnessGenerator generator = new OopProbeHarnessGenerator(null);

    @Test
    @DisplayName("Target class is embedded as a literal and used to call main")
    void buildProbeSource_WithTargetClass_EmbedsTargetAndCallsMain() {
        String source = generator.buildProbeSource("com.example.Shapes");

        assertThat(source).contains("\"com.example.Shapes\"");
        assertThat(source).doesNotContain("%TARGET_CLASS%");
        assertThat(source).doesNotContain("%PROBE_NAME%");
        assertThat(source).contains("runMain(target)");
    }

    @Test
    @DisplayName("No target class → empty literal, probe only loads student classes")
    void buildProbeSource_WithoutTargetClass_EmbedsEmptyLiteral() {
        String source = generator.buildProbeSource(null);

        assertThat(source).contains("private static final String TARGET_CLASS = \"\";");
        assertThat(source).doesNotContain("%TARGET_CLASS%");
        assertThat(source).contains("loadStudentClasses()");
    }

    @Test
    @DisplayName("Blank target class is treated as absent")
    void buildProbeSource_BlankTargetClass_EmbedsEmptyLiteral() {
        String source = generator.buildProbeSource("   ");

        assertThat(source).contains("private static final String TARGET_CLASS = \"\";");
    }

    @Test
    @DisplayName("Quotes and backslashes in the class name are escaped")
    void buildProbeSource_WeirdTargetClass_EscapesLiteral() {
        String source = generator.buildProbeSource("a\"b\\c");

        assertThat(source).contains("\"a\\\"b\\\\c\"");
    }

    @Test
    @DisplayName("Probe class name placeholder is fully replaced")
    void buildProbeSource_ReplacesProbeClassName() {
        String source = generator.buildProbeSource("Main");

        assertThat(source).contains("public final class __OopMetricsProbe");
        assertThat(source).contains("__OopMetricsProbe.class.getClassLoader()");
    }
}
