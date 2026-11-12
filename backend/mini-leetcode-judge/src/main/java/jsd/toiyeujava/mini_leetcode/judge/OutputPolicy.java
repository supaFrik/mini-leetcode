package jsd.toiyeujava.mini_leetcode.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * Cách so stdout với output kỳ vọng, cấu hình theo đề trong {@code problems.grading_config}:
 * <pre>{"output": {"mode": "normalized", "ignoreCase": true}}</pre>
 *
 * <ul>
 *   <li>{@code exact} (mặc định, như trước): chỉ bỏ khoảng trắng cuối dòng và dòng trống ở cuối.</li>
 *   <li>{@code normalized}: bỏ khoảng trắng đầu / cuối dòng, gộp nhiều khoảng trắng thành một, bỏ mọi dòng trống —
 *       phần trình bày không làm sai bài, chỉ nội dung mới quan trọng (hướng dẫn viết test của Hyperskill hs-test).</li>
 *   <li>{@code ignoreCase}: không phân biệt hoa / thường (dùng với cả hai chế độ).</li>
 * </ul>
 * Giá trị lạ hoặc JSON lỗi ⇒ {@link #EXACT}, để bài cũ không đổi kết quả.
 */
public record OutputPolicy(Mode mode, boolean ignoreCase) {

    public enum Mode { EXACT, NORMALIZED }

    public static final OutputPolicy EXACT = new OutputPolicy(Mode.EXACT, false);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static OutputPolicy fromGradingConfig(String gradingConfigJson) {
        if (gradingConfigJson == null || gradingConfigJson.isBlank()) {
            return EXACT;
        }
        try {
            JsonNode output = JSON.readTree(gradingConfigJson).path("output");
            if (!output.isObject()) {
                return EXACT;
            }
            Mode mode = "normalized".equals(output.path("mode").asText("exact").toLowerCase(Locale.ROOT))
                    ? Mode.NORMALIZED : Mode.EXACT;
            return new OutputPolicy(mode, output.path("ignoreCase").asBoolean(false));
        } catch (Exception e) {
            return EXACT;
        }
    }

    /** Mô tả ngắn cho thông báo lỗi, vd. "ignoring extra spaces and blank lines". Rỗng với {@link #EXACT}. */
    public String describe() {
        if (mode == Mode.EXACT && !ignoreCase) {
            return "";
        }
        String spaces = mode == Mode.NORMALIZED ? "extra spaces and blank lines" : "";
        String casing = ignoreCase ? "letter case" : "";
        return "ignoring " + (spaces.isEmpty() ? casing : casing.isEmpty() ? spaces : spaces + " and " + casing);
    }
}
