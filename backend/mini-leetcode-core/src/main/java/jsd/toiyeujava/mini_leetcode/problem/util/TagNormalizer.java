package jsd.toiyeujava.mini_leetcode.problem.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chuẩn hoá tên chủ đề (tag) của bài tập.
 *
 * <ul>
 *   <li>Trim khoảng trắng hai đầu, loại bỏ tag rỗng.</li>
 *   <li>Khử trùng lặp không phân biệt hoa/thường, giữ nguyên cách viết
 *       (display casing) của lần xuất hiện đầu tiên.</li>
 *   <li>Giới hạn độ dài theo cột {@code problem_tags.tag VARCHAR(50)}.</li>
 * </ul>
 *
 * Lọc theo tag ở tầng SQL dùng so khớp chính xác không phân biệt hoa/thường
 * với ngữ nghĩa OR: bài chỉ cần thuộc ÍT NHẤT MỘT tag trong danh sách.
 */
public final class TagNormalizer {

    /** Khớp cột {@code problem_tags.tag VARCHAR(50)}. */
    public static final int MAX_TAG_LENGTH = 50;

    private TagNormalizer() {
    }

    /**
     * Chuẩn hoá danh sách tag đầu vào. {@code null} → danh sách rỗng.
     *
     * @throws IllegalArgumentException nếu tag vượt quá {@link #MAX_TAG_LENGTH} ký tự
     */
    public static List<String> normalize(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Map<String, String> dedup = new LinkedHashMap<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(
                        "Tag must not exceed " + MAX_TAG_LENGTH + " characters: " + trimmed);
            }
            dedup.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(dedup.values());
    }
}
