package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;

/**
 * Thông báo lỗi cụ thể cho học viên (theo hướng dẫn viết test của Hyperskill hs-test: nói rõ kỳ vọng gì,
 * chương trình in gì, và mỗi loại lỗi một thông báo riêng).
 *
 * <ul>
 *   <li>Testcase công khai: dòng lệch đầu tiên, kèm nội dung kỳ vọng và nội dung học viên in ra.</li>
 *   <li>Testcase ẩn: chỉ số dòng lệch và nhãn (vd. "stage 1: two-accounts") — không lộ dữ liệu ẩn.</li>
 * </ul>
 */
public final class TestFeedback {

    private static final int MAX_LINE_CHARS = 200;

    private TestFeedback() {
    }

    /** "test case #3 (hidden · stage 1: two-accounts)" — {@code index} bắt đầu từ 1. */
    public static String caseName(int index, TestCase testCase) {
        boolean hidden = testCase == null || !Boolean.FALSE.equals(testCase.getIsHidden());
        String label = testCase != null ? testCase.getLabel() : null;
        StringBuilder sb = new StringBuilder("test case #").append(index);
        if (hidden || (label != null && !label.isBlank())) {
            sb.append(" (");
            if (hidden) {
                sb.append("hidden");
            }
            if (label != null && !label.isBlank()) {
                sb.append(hidden ? " · " : "").append(label.strip());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    public static String wrongAnswer(int index, TestCase testCase, String actual, String expected, OutputPolicy policy) {
        boolean hidden = testCase == null || !Boolean.FALSE.equals(testCase.getIsHidden());
        StringBuilder sb = new StringBuilder("Wrong answer on ").append(caseName(index, testCase));
        String compared = policy != null ? policy.describe() : "";
        if (!compared.isEmpty()) {
            sb.append(" — compared ").append(compared);
        }
        OutputComparator.firstDifference(actual, expected, policy).ifPresent(d -> {
            sb.append('\n');
            if (hidden) {
                if (d.actual() == null) {
                    sb.append("Your output ended too early: line ").append(d.line()).append(" is missing.");
                } else if (d.expected() == null) {
                    sb.append("Your output has extra lines starting at line ").append(d.line()).append('.');
                } else {
                    sb.append("Your output differs from the expected output at line ").append(d.line()).append('.');
                }
                return;
            }
            sb.append("Line ").append(d.line()).append(": ");
            if (d.actual() == null) {
                sb.append("expected ").append(quote(d.expected())).append(" but your output ended");
            } else if (d.expected() == null) {
                sb.append("unexpected extra output ").append(quote(d.actual()));
            } else {
                sb.append("expected ").append(quote(d.expected())).append('\n')
                        .append("        but got  ").append(quote(d.actual()));
            }
        });
        return sb.toString();
    }

    /** TLE / MLE / RE / SV: dòng tiêu đề chỉ ra testcase; RE kèm stderr (đã cắt ngắn ở nơi gọi). */
    public static String failure(SubmissionVerdict verdict, int index, TestCase testCase, String stderr) {
        String what = switch (verdict) {
            case TLE -> "Time limit exceeded";
            case MLE -> "Memory limit exceeded";
            case RE -> "Runtime error";
            case SV -> "Sandbox violation";
            default -> verdict.getDescription();
        };
        String head = what + " on " + caseName(index, testCase);
        return stderr == null || stderr.isBlank() ? head : head + "\n" + stderr;
    }

    private static String quote(String line) {
        String s = line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS) + "…" : line;
        return "\"" + s + "\"";
    }
}
