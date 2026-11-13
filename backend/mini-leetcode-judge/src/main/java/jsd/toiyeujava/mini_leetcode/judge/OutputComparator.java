package jsd.toiyeujava.mini_leetcode.judge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class OutputComparator {

    private OutputComparator() {
    }

    /**
     * Dòng đầu tiên khác nhau (đánh số từ 1 trên các dòng đã chuẩn hoá theo {@link OutputPolicy}).
     * {@code expected == null}: học viên in thừa; {@code actual == null}: output dừng sớm.
     */
    public record Difference(int line, String expected, String actual) {
    }

    public static boolean matches(String actual, String expected) {
        return matches(actual, expected, OutputPolicy.EXACT);
    }

    public static boolean matches(String actual, String expected, OutputPolicy policy) {
        return firstDifference(actual, expected, policy).isEmpty();
    }

    public static Optional<Difference> firstDifference(String actual, String expected, OutputPolicy policy) {
        OutputPolicy p = policy != null ? policy : OutputPolicy.EXACT;
        List<String> a = lines(actual, p);
        List<String> e = lines(expected, p);
        int n = Math.max(a.size(), e.size());
        for (int i = 0; i < n; i++) {
            String al = i < a.size() ? a.get(i) : null;
            String el = i < e.size() ? e.get(i) : null;
            if (al == null || el == null || !same(al, el, p)) {
                return Optional.of(new Difference(i + 1, el, al));
            }
        }
        return Optional.empty();
    }

    private static boolean same(String a, String b, OutputPolicy p) {
        return p.ignoreCase() ? a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT)) : a.equals(b);
    }

    /** EXACT: bỏ khoảng trắng cuối dòng + dòng trống ở cuối (hành vi cũ). NORMALIZED: trim, gộp khoảng trắng, bỏ dòng trống. */
    private static List<String> lines(String text, OutputPolicy p) {
        String unified = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
        List<String> out = new ArrayList<>();
        for (String line : unified.split("\n", -1)) {
            if (p.mode() == OutputPolicy.Mode.NORMALIZED) {
                String collapsed = line.strip().replaceAll("\\s+", " ");
                if (!collapsed.isEmpty()) {
                    out.add(collapsed);
                }
            } else {
                out.add(line.stripTrailing());
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
