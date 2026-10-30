package jsd.toiyeujava.mini_leetcode.judge;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc và tách sentinel peak memory do wrapper {@code sh -c} ghi ra stderr.
 *
 * <p>Wrapper trong sandbox in ra một dòng dạng
 * {@code __MEM_PEAK_KB__=162484224} (giá trị byte của
 * {@code /sys/fs/cgroup/memory.peak}). Dòng này chỉ dành cho hệ thống, phải
 * được loại khỏi stderr mà người dùng nhìn thấy.
 */
final class MemoryProbe {

    private final Integer peakMemoryKb;
    private final String cleanStderr;

    private MemoryProbe(Integer peakMemoryKb, String cleanStderr) {
        this.peakMemoryKb = peakMemoryKb;
        this.cleanStderr = cleanStderr;
    }

    /**
     * Tách sentinel khỏi stderr.
     *
     * @param stderr stderr thô của container (có thể null)
     * @return peak memory (KB) và stderr đã loại bỏ sentinel
     */
    static MemoryProbe parse(String stderr) {
        if (stderr == null || stderr.isEmpty()) {
            return new MemoryProbe(null, stderr == null ? "" : stderr);
        }
        if (!stderr.contains(LanguageRuntime.MEMORY_SENTINEL)) {
            return new MemoryProbe(null, stderr);
        }

        Integer peakKb = null;
        List<String> kept = new ArrayList<>();
        for (String line : stderr.split("\n", -1)) {
            int index = line.indexOf(LanguageRuntime.MEMORY_SENTINEL);
            if (index < 0) {
                kept.add(line);
                continue;
            }
            String value = line.substring(index + LanguageRuntime.MEMORY_SENTINEL.length()).trim();
            peakKb = parseKb(value);
            // Giữ phần đầu của dòng (nếu có) trước sentinel.
            String prefix = line.substring(0, index);
            if (!prefix.isBlank()) {
                kept.add(prefix);
            }
        }
        String cleaned = String.join("\n", kept);
        return new MemoryProbe(peakKb, cleaned);
    }

    private static Integer parseKb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long bytes = Long.parseLong(value.trim());
            if (bytes < 0) {
                return null;
            }
            return (int) Math.min(Integer.MAX_VALUE, bytes / 1024);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Peak memory tính bằng KB, {@code null} nếu không đọc được sentinel. */
    Integer peakMemoryKb() {
        return peakMemoryKb;
    }

    /** stderr đã loại bỏ dòng sentinel. */
    String cleanStderr() {
        return cleanStderr;
    }
}
