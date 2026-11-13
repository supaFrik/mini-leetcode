package jsd.toiyeujava.mini_leetcode.topic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm tra nội dung khi soạn, chấm bài học viên và tạo nội dung công khai cho từng loại step
 * (bảng "Nội dung từng loại step" trong specs/topics.spec.md). Không phụ thuộc Spring để test thuần.
 */
public class StepGrader {

    /** Ô trống trong FILL_BLANK: {@code [[id]]}. */
    public static final Pattern BLANK = Pattern.compile("\\[\\[([A-Za-z0-9_-]{1,20})]]");

    private final ObjectMapper mapper;

    public StepGrader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Grade(boolean correct, String message, String explanation) {}

    // ── Kiểm tra khi soạn ───────────────────────────────────────────────────

    /** Ném {@link IllegalArgumentException} (400) với thông báo có vị trí {@code where} khi nội dung sai. */
    public void validate(TopicStepType type, JsonNode content, JsonNode answer, String where) {
        require(content != null && content.isObject(), where + ": content must be a JSON object");
        switch (type) {
            case THEORY -> require(text(content, "markdown") != null, where + ": theory needs markdown");
            case CHOICE -> {
                requirePrompt(content, where);
                List<String> ids = items(content, "options", 2, 10, where);
                List<String> correct = stringList(answer, "correct", where);
                require(!correct.isEmpty(), where + ": choose at least one correct option");
                require(ids.containsAll(correct), where + ": a correct option id does not exist");
                require(content.path("multiple").asBoolean(false) || correct.size() == 1,
                        where + ": a single-answer question needs exactly one correct option");
            }
            case MATCHING -> {
                requirePrompt(content, where);
                List<String> left = items(content, "left", 2, 10, where);
                List<String> right = items(content, "right", 2, 10, where);
                require(left.size() == right.size(), where + ": left and right need the same number of items");
                JsonNode pairs = answer == null ? null : answer.get("pairs");
                require(pairs != null && pairs.isObject(), where + ": answer.pairs is required");
                Set<String> used = new HashSet<>();
                for (String l : left) {
                    String r = pairs.path(l).asText(null);
                    require(r != null && right.contains(r), where + ": pair for '" + l + "' is missing or unknown");
                    require(used.add(r), where + ": '" + r + "' is matched twice");
                }
            }
            case ORDERING -> {
                requirePrompt(content, where);
                List<String> ids = items(content, "items", 2, 15, where);
                List<String> order = stringList(answer, "order", where);
                require(order.size() == ids.size() && new HashSet<>(order).equals(new HashSet<>(ids)),
                        where + ": answer.order must list every item exactly once");
            }
            case FILL_BLANK -> {
                requirePrompt(content, where);
                String code = text(content, "code");
                require(code != null, where + ": fill-in-the-blank needs code");
                List<String> blanks = blankIds(code);
                require(!blanks.isEmpty(), where + ": code needs at least one blank written as [[id]]");
                require(new HashSet<>(blanks).size() == blanks.size(), where + ": blank ids must be unique");
                JsonNode accepted = answer == null ? null : answer.get("blanks");
                require(accepted != null && accepted.isObject(), where + ": answer.blanks is required");
                for (String id : blanks) {
                    JsonNode list = accepted.get(id);
                    require(list != null && list.isArray() && !list.isEmpty(),
                            where + ": blank '" + id + "' needs at least one accepted answer");
                    list.forEach(v -> require(v.isTextual() && !v.asText().isBlank(),
                            where + ": accepted answers for '" + id + "' must be non-empty text"));
                }
            }
            case CODE -> { /* problemId do service kiểm tra */ }
        }
    }

    // ── Chấm ────────────────────────────────────────────────────────────────

    public Grade grade(TopicStepType type, JsonNode content, JsonNode answer, JsonNode submitted) {
        String explanation = answer == null ? null : text(answer, "explanation");
        JsonNode in = submitted == null ? mapper.createObjectNode() : submitted;
        return switch (type) {
            case THEORY -> new Grade(true, "Marked as read", null);
            case CHOICE -> {
                Set<String> picked = new HashSet<>(stringListOrEmpty(in.get("selected")));
                Set<String> correct = new HashSet<>(stringListOrEmpty(answer.get("correct")));
                boolean ok = picked.equals(correct);
                String hint = correct.size() > 1 ? "Not quite. This question has " + correct.size() + " correct options."
                        : "Not quite. Try again.";
                yield new Grade(ok, ok ? "Correct!" : hint, ok ? explanation : null);
            }
            case MATCHING -> {
                JsonNode expected = answer.get("pairs");
                JsonNode given = in.path("pairs");
                int total = expected.size();
                int right = 0;
                for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> e = it.next();
                    if (e.getValue().asText().equals(given.path(e.getKey()).asText(null))) {
                        right++;
                    }
                }
                boolean ok = right == total;
                yield new Grade(ok, ok ? "Correct!" : right + " of " + total + " pairs are correct.", ok ? explanation : null);
            }
            case ORDERING -> {
                List<String> expected = stringListOrEmpty(answer.get("order"));
                List<String> given = stringListOrEmpty(in.get("order"));
                int right = 0;
                for (int i = 0; i < expected.size(); i++) {
                    if (i < given.size() && expected.get(i).equals(given.get(i))) {
                        right++;
                    }
                }
                boolean ok = right == expected.size() && given.size() == expected.size();
                yield new Grade(ok, ok ? "Correct!" : right + " of " + expected.size() + " lines are in the right place.",
                        ok ? explanation : null);
            }
            case FILL_BLANK -> {
                boolean caseSensitive = answer.path("caseSensitive").asBoolean(false);
                List<String> blanks = blankIds(text(content, "code"));
                List<Integer> wrong = new ArrayList<>();
                for (int i = 0; i < blanks.size(); i++) {
                    String value = normalize(in.path("values").path(blanks.get(i)).asText(""), caseSensitive);
                    boolean match = false;
                    for (JsonNode accepted : answer.path("blanks").path(blanks.get(i))) {
                        match |= normalize(accepted.asText(), caseSensitive).equals(value);
                    }
                    if (!match) {
                        wrong.add(i + 1);
                    }
                }
                boolean ok = wrong.isEmpty();
                String message = ok ? "Correct!" : (wrong.size() == 1 ? "Blank " + wrong.get(0) + " is not right yet."
                        : "Blanks " + join(wrong) + " are not right yet.");
                yield new Grade(ok, message, ok ? explanation : null);
            }
            case CODE -> throw new IllegalArgumentException("A code step is completed by an accepted submission to its problem");
        };
    }

    // ── Nội dung công khai ──────────────────────────────────────────────────

    /**
     * Bản gửi cho học viên: không có đáp án; cột phải (MATCHING) và các dòng (ORDERING) được xáo trộn
     * ổn định theo {@code seed} (id của step) để thứ tự nhập không lộ đáp án.
     */
    public JsonNode publicContent(TopicStepType type, JsonNode content, JsonNode answer, long seed) {
        ObjectNode copy = content.deepCopy();
        if (type == TopicStepType.MATCHING) {
            copy.set("right", shuffled((ArrayNode) copy.get("right"), seed, null));
        } else if (type == TopicStepType.ORDERING) {
            copy.set("items", shuffled((ArrayNode) copy.get("items"), seed, stringListOrEmpty(answer.get("order"))));
        } else if (type == TopicStepType.FILL_BLANK) {
            ArrayNode ids = copy.putArray("blanks");
            blankIds(text(content, "code")).forEach(ids::add);
        }
        return copy;
    }

    /** Xáo trộn; với ORDERING tránh trả đúng thứ tự đáp án (xoay thêm một vị trí nếu trùng). */
    private ArrayNode shuffled(ArrayNode array, long seed, List<String> mustDifferFrom) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        Collections.shuffle(list, new Random(seed * 31 + 7));
        if (mustDifferFrom != null && list.size() > 1
                && list.stream().map(n -> n.path("id").asText()).toList().equals(mustDifferFrom)) {
            Collections.rotate(list, 1);
        }
        ArrayNode out = mapper.createArrayNode();
        list.forEach(out::add);
        return out;
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    public static List<String> blankIds(String code) {
        List<String> ids = new ArrayList<>();
        if (code == null) {
            return ids;
        }
        Matcher m = BLANK.matcher(code);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static String normalize(String s, boolean caseSensitive) {
        String t = s.trim().replaceAll("\\s+", " ");
        return caseSensitive ? t : t.toLowerCase(Locale.ROOT);
    }

    private static String join(List<Integer> numbers) {
        List<String> parts = numbers.stream().map(String::valueOf).toList();
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static void requirePrompt(JsonNode content, String where) {
        require(text(content, "prompt") != null, where + ": prompt is required");
    }

    /** Mảng {@code [{id, text}]} có độ dài trong [min, max], id không trùng; trả danh sách id. */
    private static List<String> items(JsonNode content, String field, int min, int max, String where) {
        JsonNode array = content.get(field);
        require(array != null && array.isArray(), where + ": " + field + " must be a list");
        require(array.size() >= min && array.size() <= max,
                where + ": " + field + " needs " + min + " to " + max + " items");
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode item : array) {
            String id = text(item, "id");
            require(id != null && text(item, "text") != null, where + ": every " + field + " item needs id and text");
            require(ids.add(id), where + ": duplicate id '" + id + "' in " + field);
        }
        return new ArrayList<>(ids);
    }

    private static List<String> stringList(JsonNode answer, String field, String where) {
        JsonNode array = answer == null ? null : answer.get(field);
        require(array != null && array.isArray(), where + ": answer." + field + " must be a list");
        return stringListOrEmpty(array);
    }

    private static List<String> stringListOrEmpty(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(v -> out.add(v.asText()));
        }
        return out;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
