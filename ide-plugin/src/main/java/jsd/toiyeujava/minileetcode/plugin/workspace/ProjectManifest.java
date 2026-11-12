package jsd.toiyeujava.minileetcode.plugin.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Lightweight, zero-dependency parser and serializer for the {@code .mlc/project.yaml} metadata manifest.
 * Adheres strictly to the Ponytail principle (zero third-party dependencies outside standard JDK 21).
 * <p>
 * Manages study plan project binding:
 * <ul>
 *     <li>{@code plan}: study plan slug (e.g. {@code "oop-with-java"})</li>
 *     <li>{@code item}: item identifier or position (e.g. {@code "1"})</li>
 *     <li>{@code current_stage}: current active stage number (1-based, e.g. {@code 1})</li>
 *     <li>{@code problem_slug}: problem slug for current stage (e.g. {@code "mini-bank-stage-1"})</li>
 *     <li>{@code problem_id}: optional problem database ID (nullable, e.g. {@code 101L})</li>
 *     <li>Additional arbitrary key-value properties preserved across read/write cycles.</li>
 * </ul>
 * Instances are immutable, thread-safe, and support builder and evolution helpers.
 */
public final class ProjectManifest {

    public static final String MLC_DIR = ".mlc";
    public static final String MANIFEST_FILE_NAME = "project.yaml";
    public static final Path RELATIVE_PATH = Path.of(MLC_DIR, MANIFEST_FILE_NAME);

    private final String plan;
    private final String item;
    private final int currentStage;
    private final String problemSlug;
    private final Long problemId;
    private final Map<String, String> extraProperties;

    public ProjectManifest(String plan, String item, int currentStage, String problemSlug) {
        this(plan, item, currentStage, problemSlug, null, Collections.emptyMap());
    }

    public ProjectManifest(String plan, String item, int currentStage, String problemSlug, Long problemId) {
        this(plan, item, currentStage, problemSlug, problemId, Collections.emptyMap());
    }

    public ProjectManifest(
            String plan,
            String item,
            int currentStage,
            String problemSlug,
            Long problemId,
            Map<String, String> extraProperties
    ) {
        if (plan == null || plan.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'plan'");
        }
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'item'");
        }
        if (currentStage <= 0) {
            throw new IllegalArgumentException("Invalid 'current_stage': must be a positive integer (>= 1), got: " + currentStage);
        }
        if (problemSlug == null || problemSlug.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'problem_slug'");
        }

        this.plan = plan.trim();
        this.item = item.trim();
        this.currentStage = currentStage;
        this.problemSlug = problemSlug.trim();
        this.problemId = problemId;

        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (extraProperties != null) {
            for (Map.Entry<String, String> entry : extraProperties.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                    String normKey = normalizeKey(entry.getKey());
                    if (!isStandardKey(normKey)) {
                        map.put(entry.getKey().trim(), entry.getValue());
                    }
                }
            }
        }
        this.extraProperties = Collections.unmodifiableMap(map);
    }

    // --- Accessors & Getters ---

    public String plan() {
        return plan;
    }

    public String getPlan() {
        return plan;
    }

    public String item() {
        return item;
    }

    public String getItem() {
        return item;
    }

    public int currentStage() {
        return currentStage;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public String problemSlug() {
        return problemSlug;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public Long problemId() {
        return problemId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public Optional<Long> findProblemId() {
        return Optional.ofNullable(problemId);
    }

    public int itemAsInt() {
        return Integer.parseInt(item);
    }

    public OptionalInt findItemAsInt() {
        try {
            return OptionalInt.of(Integer.parseInt(item));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public Map<String, String> extraProperties() {
        return extraProperties;
    }

    public Map<String, String> getExtraProperties() {
        return extraProperties;
    }

    public String get(String key) {
        if (key == null) return null;
        String norm = normalizeKey(key);
        return switch (norm) {
            case "plan" -> plan;
            case "item" -> item;
            case "current_stage" -> String.valueOf(currentStage);
            case "problem_slug" -> problemSlug;
            case "problem_id" -> problemId != null ? String.valueOf(problemId) : null;
            default -> extraProperties.get(key);
        };
    }

    public Optional<String> find(String key) {
        return Optional.ofNullable(get(key));
    }

    public boolean hasExtra(String key) {
        return key != null && extraProperties.containsKey(key);
    }

    // --- Evolution & Mutation Helpers ---

    public ProjectManifest withStage(int newStage, String newProblemSlug, Long newProblemId) {
        return toBuilder()
                .currentStage(newStage)
                .problemSlug(newProblemSlug)
                .problemId(newProblemId)
                .build();
    }

    public ProjectManifest withCurrentStage(int newStage) {
        return toBuilder().currentStage(newStage).build();
    }

    public ProjectManifest withProblem(String newProblemSlug, Long newProblemId) {
        return toBuilder().problemSlug(newProblemSlug).problemId(newProblemId).build();
    }

    public ProjectManifest withExtra(String key, String value) {
        Builder b = toBuilder();
        b.extra(key, value);
        return b.build();
    }

    // --- Builder Pattern ---

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder()
                .plan(this.plan)
                .item(this.item)
                .currentStage(this.currentStage)
                .problemSlug(this.problemSlug)
                .problemId(this.problemId);
        this.extraProperties.forEach(b::extra);
        return b;
    }

    public static ProjectManifest of(String plan, String item, int currentStage, String problemSlug) {
        return of(plan, item, currentStage, problemSlug, null);
    }

    public static ProjectManifest of(String plan, String item, int currentStage, String problemSlug, Long problemId) {
        return builder()
                .plan(plan)
                .item(item)
                .currentStage(currentStage)
                .problemSlug(problemSlug)
                .problemId(problemId)
                .build();
    }

    public static final class Builder {
        private String plan;
        private String item;
        private int currentStage = 1;
        private String problemSlug;
        private Long problemId;
        private final Map<String, String> extraProperties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        public Builder plan(String plan) {
            this.plan = plan;
            return this;
        }

        public Builder item(String item) {
            this.item = item;
            return this;
        }

        public Builder item(int itemPosition) {
            this.item = String.valueOf(itemPosition);
            return this;
        }

        public Builder currentStage(int currentStage) {
            this.currentStage = currentStage;
            return this;
        }

        public Builder problemSlug(String problemSlug) {
            this.problemSlug = problemSlug;
            return this;
        }

        public Builder problemId(Long problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder extra(String key, String value) {
            if (key != null && !key.isBlank()) {
                if (value == null) {
                    this.extraProperties.remove(key.trim());
                } else {
                    this.extraProperties.put(key.trim(), value);
                }
            }
            return this;
        }

        public Builder extraProperties(Map<String, String> extras) {
            if (extras != null) {
                extras.forEach(this::extra);
            }
            return this;
        }

        public ProjectManifest build() {
            return new ProjectManifest(plan, item, currentStage, problemSlug, problemId, extraProperties);
        }
    }

    // --- Parser Engine ---

    public static ProjectManifest parse(String yamlContent) {
        if (yamlContent == null) {
            throw new IllegalArgumentException("YAML content must not be null");
        }

        String parsedPlan = null;
        String parsedItem = null;
        Integer parsedStage = null;
        String parsedSlug = null;
        Long parsedId = null;
        Map<String, String> extras = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        List<String> lines = yamlContent.lines().toList();
        for (int lineNum = 1; lineNum <= lines.size(); lineNum++) {
            String line = lines.get(lineNum - 1).trim();
            if (line.isEmpty() || line.startsWith("#") || line.equals("---") || line.equals("...")) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                throw new IllegalArgumentException("Malformed manifest at line " + lineNum + " (missing ':' delimiter): \"" + line + "\"");
            }

            String rawKey = line.substring(0, colonIndex).trim();
            if (rawKey.isEmpty()) {
                throw new IllegalArgumentException("Malformed manifest at line " + lineNum + " (empty key): \"" + line + "\"");
            }
            if ((rawKey.startsWith("\"") && rawKey.endsWith("\"")) || (rawKey.startsWith("'") && rawKey.endsWith("'"))) {
                rawKey = rawKey.substring(1, rawKey.length() - 1).trim();
            }

            String rawValue = line.substring(colonIndex + 1).trim();
            String parsedValue = parseYamlValue(rawValue);

            String normalizedKey = normalizeKey(rawKey);
            switch (normalizedKey) {
                case "plan" -> parsedPlan = parsedValue;
                case "item" -> parsedItem = parsedValue;
                case "current_stage" -> {
                    if (parsedValue == null || parsedValue.isBlank()) {
                        throw new IllegalArgumentException("Empty 'current_stage' at line " + lineNum);
                    }
                    try {
                        parsedStage = Integer.parseInt(parsedValue.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid integer for 'current_stage' at line " + lineNum + ": \"" + parsedValue + "\"");
                    }
                }
                case "problem_slug" -> parsedSlug = parsedValue;
                case "problem_id" -> {
                    if (parsedValue == null || parsedValue.isBlank() || "null".equalsIgnoreCase(parsedValue) || "~".equals(parsedValue)) {
                        parsedId = null;
                    } else {
                        try {
                            parsedId = Long.parseLong(parsedValue.trim());
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid integer for 'problem_id' at line " + lineNum + ": \"" + parsedValue + "\"");
                        }
                    }
                }
                default -> extras.put(rawKey, parsedValue);
            }
        }

        if (parsedPlan == null || parsedPlan.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'plan'");
        }
        if (parsedItem == null || parsedItem.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'item'");
        }
        if (parsedStage == null) {
            throw new IllegalArgumentException("Missing required manifest field: 'current_stage'");
        }
        if (parsedStage <= 0) {
            throw new IllegalArgumentException("Invalid 'current_stage': must be a positive integer (>= 1), got: " + parsedStage);
        }
        if (parsedSlug == null || parsedSlug.isBlank()) {
            throw new IllegalArgumentException("Missing required manifest field: 'problem_slug'");
        }

        return new ProjectManifest(parsedPlan, parsedItem, parsedStage, parsedSlug, parsedId, extras);
    }

    private static String parseYamlValue(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.startsWith("\"")) {
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            int endIdx = -1;
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (escaped) {
                    switch (c) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        default -> sb.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    endIdx = i;
                    break;
                } else {
                    sb.append(c);
                }
            }
            if (endIdx != -1) {
                return sb.toString();
            }
            return sb.toString();
        }
        if (s.startsWith("'")) {
            StringBuilder sb = new StringBuilder();
            int endIdx = -1;
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        sb.append('\'');
                        i++;
                    } else {
                        endIdx = i;
                        break;
                    }
                } else {
                    sb.append(c);
                }
            }
            if (endIdx != -1) {
                return sb.toString();
            }
            return sb.toString();
        }

        // Unquoted: check for inline comments preceded by whitespace
        int commentIdx = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' && (i == 0 || Character.isWhitespace(s.charAt(i - 1)))) {
                commentIdx = i;
                break;
            }
        }
        if (commentIdx != -1) {
            s = s.substring(0, commentIdx).trim();
        }
        return s;
    }

    private static String normalizeKey(String key) {
        String lower = key.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "plan", "study_plan", "study-plan", "studyplan" -> "plan";
            case "item", "item_id", "item-id", "itemid" -> "item";
            case "current_stage", "current-stage", "currentstage", "stage", "current_stage_no" -> "current_stage";
            case "problem_slug", "problem-slug", "problemslug", "slug" -> "problem_slug";
            case "problem_id", "problem-id", "problemid", "id" -> "problem_id";
            default -> lower;
        };
    }

    private static boolean isStandardKey(String normalizedKey) {
        return "plan".equals(normalizedKey)
                || "item".equals(normalizedKey)
                || "current_stage".equals(normalizedKey)
                || "problem_slug".equals(normalizedKey)
                || "problem_id".equals(normalizedKey);
    }

    // --- Serialization Engine ---

    public String toYamlString() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Mini LeetCode Project Manifest (.mlc/project.yaml)\n");
        sb.append("plan: ").append(formatYamlValue(plan)).append("\n");
        sb.append("item: ").append(formatYamlValue(item)).append("\n");
        sb.append("current_stage: ").append(currentStage).append("\n");
        sb.append("problem_slug: ").append(formatYamlValue(problemSlug)).append("\n");
        if (problemId != null) {
            sb.append("problem_id: ").append(problemId).append("\n");
        }
        for (Map.Entry<String, String> entry : extraProperties.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(formatYamlValue(entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    private static String formatYamlValue(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.isEmpty()
                || value.trim().length() != value.length()
                || value.contains(":")
                || value.contains("#")
                || value.contains("\"")
                || value.contains("'")
                || value.contains("\n")
                || value.contains("\r")
                || value.startsWith("-")
                || value.startsWith("@")
                || value.startsWith("`")
                || "null".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)
                || "~".equals(value);

        if (!needsQuotes) {
            return value;
        }

        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    // --- File I/O Helpers ---

    public static ProjectManifest readFrom(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new NoSuchFileException("Manifest file does not exist: " + path.toAbsolutePath());
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return parse(content);
    }

    public void writeTo(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                path,
                toYamlString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    public static Path resolvePath(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot must not be null");
        return projectRoot.resolve(RELATIVE_PATH);
    }

    public static boolean existsIn(Path projectRoot) {
        if (projectRoot == null) return false;
        return Files.isRegularFile(resolvePath(projectRoot));
    }

    public static ProjectManifest readFromProject(Path projectRoot) throws IOException {
        return readFrom(resolvePath(projectRoot));
    }

    public void writeToProject(Path projectRoot) throws IOException {
        writeTo(resolvePath(projectRoot));
    }

    // --- Identity Methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectManifest that = (ProjectManifest) o;
        return currentStage == that.currentStage
                && Objects.equals(plan, that.plan)
                && Objects.equals(item, that.item)
                && Objects.equals(problemSlug, that.problemSlug)
                && Objects.equals(problemId, that.problemId)
                && Objects.equals(extraProperties, that.extraProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plan, item, currentStage, problemSlug, problemId, extraProperties);
    }

    @Override
    public String toString() {
        return "ProjectManifest[" +
                "plan='" + plan + '\'' +
                ", item='" + item + '\'' +
                ", currentStage=" + currentStage +
                ", problemSlug='" + problemSlug + '\'' +
                ", problemId=" + problemId +
                ", extraProperties=" + extraProperties +
                ']';
    }
}
