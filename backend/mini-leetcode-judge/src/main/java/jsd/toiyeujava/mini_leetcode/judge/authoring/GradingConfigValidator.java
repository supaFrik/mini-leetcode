package jsd.toiyeujava.mini_leetcode.judge.authoring;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopConstraintRule;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopConstructorConstraint;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopFieldConstraint;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopMethodConstraint;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.SocketScenarioConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.SocketStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiểm tra {@code problems.grading_config} trước khi lưu, dùng đúng các lớp model mà judge đọc lúc chấm.
 *
 * <p>Vì sao cần? Lúc chấm, judge đọc cấu hình rất "dễ dãi": các model đều
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} nên key gõ sai bị bỏ qua, và JSON lỗi bị coi như
 * cấu hình rỗng. Giảng viên sẽ không biết rule của mình không có hiệu lực. Ở đây ta báo lỗi rõ theo
 * đường dẫn ({@code rules[0].methods[1].retunType: unknown key}).
 */
@Component
public class GradingConfigValidator {

    /** Modifier Java hợp lệ cho requiredModifiers / forbiddenModifiers (chữ thường, như ModifierHelper đọc). */
    static final Set<String> JAVA_MODIFIERS = Set.of(
            "public", "protected", "private", "static", "final", "abstract", "synchronized",
            "native", "transient", "volatile", "strictfp", "interface");

    private static final Set<String> ROOT_KEYS = Set.of("rules", "socketScenarios", "output");
    private static final Set<String> OUTPUT_KEYS = Set.of("mode", "ignoreCase");
    private static final Set<String> OUTPUT_MODES = Set.of("exact", "normalized");

    public record Result(List<String> errors, int ruleCount, int socketScenarioCount, double totalRuleWeight) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private final ObjectMapper mapper;
    private final Map<Class<?>, Set<String>> propertyCache = new ConcurrentHashMap<>();

    public GradingConfigValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@code "output": {"mode": "exact" | "normalized", "ignoreCase": bool}} — xem OutputPolicy. */
    private static void checkOutput(JsonNode output, List<String> errors) {
        if (output == null || output.isNull()) {
            return;
        }
        if (!output.isObject()) {
            errors.add("output: must be an object like {\"mode\": \"normalized\", \"ignoreCase\": false}");
            return;
        }
        output.fieldNames().forEachRemaining(key -> {
            if (!OUTPUT_KEYS.contains(key)) {
                errors.add("output." + key + ": unknown key (allowed: mode, ignoreCase)");
            }
        });
        JsonNode mode = output.get("mode");
        if (mode != null && !(mode.isTextual() && OUTPUT_MODES.contains(mode.asText().toLowerCase(java.util.Locale.ROOT)))) {
            errors.add("output.mode: must be \"exact\" or \"normalized\"");
        }
        JsonNode ignoreCase = output.get("ignoreCase");
        if (ignoreCase != null && !ignoreCase.isBoolean()) {
            errors.add("output.ignoreCase: must be true or false");
        }
    }

    public Result validate(String raw) {
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new Result(List.of("Grading configuration is empty"), 0, 0, 0);
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            return new Result(List.of("Not valid JSON: " + firstLine(e.getMessage())), 0, 0, 0);
        }
        if (root == null || !root.isObject()) {
            return new Result(List.of("Root must be a JSON object like {\"rules\": [...], \"socketScenarios\": [...]}"), 0, 0, 0);
        }
        root.fieldNames().forEachRemaining(key -> {
            if (!ROOT_KEYS.contains(key)) {
                errors.add(key + ": unknown key (allowed: rules, socketScenarios, output)");
            }
        });
        checkOutput(root.get("output"), errors);

        int ruleCount = 0;
        double totalWeight = 0;
        JsonNode rules = root.get("rules");
        if (rules != null && !rules.isNull()) {
            if (!rules.isArray()) {
                errors.add("rules: must be an array");
            } else {
                for (int i = 0; i < rules.size(); i++) {
                    String path = "rules[" + i + "]";
                    OopConstraintRule rule = checkObject(rules.get(i), path, OopConstraintRule.class, errors);
                    if (rule == null) {
                        continue;
                    }
                    ruleCount++;
                    totalWeight += rule.getWeight();
                    checkRule(rules.get(i), rule, path, errors);
                }
            }
        }

        int scenarioCount = 0;
        JsonNode scenarios = root.get("socketScenarios");
        if (scenarios != null && !scenarios.isNull()) {
            if (!scenarios.isArray()) {
                errors.add("socketScenarios: must be an array");
            } else {
                for (int i = 0; i < scenarios.size(); i++) {
                    String path = "socketScenarios[" + i + "]";
                    SocketScenarioConfig scenario = checkObject(scenarios.get(i), path, SocketScenarioConfig.class, errors);
                    if (scenario == null) {
                        continue;
                    }
                    scenarioCount++;
                    checkScenario(scenarios.get(i), scenario, path, errors);
                }
            }
        }
        // Chỉ có "output": bài chấm hoàn toàn bằng testcase stdin/stdout (mặc định cho bài OOP) — hợp lệ.
        if (ruleCount == 0 && scenarioCount == 0 && !root.has("output") && errors.isEmpty()) {
            errors.add("Configuration has no rules, socket scenarios or output settings — clear it instead of saving an empty config");
        }
        return new Result(List.copyOf(errors), ruleCount, scenarioCount, totalWeight);
    }

    private void checkRule(JsonNode node, OopConstraintRule rule, String path, List<String> errors) {
        if (rule.getTargetClassName() == null || rule.getTargetClassName().isBlank()) {
            errors.add(path + ".targetClassName: required");
        }
        if (rule.getWeight() < 0) {
            errors.add(path + ".weight: must not be negative");
        }
        checkModifiers(rule.getRequiredModifiers(), path + ".requiredModifiers", errors);
        checkModifiers(rule.getForbiddenModifiers(), path + ".forbiddenModifiers", errors);
        checkChildren(node.get("fields"), path + ".fields", OopFieldConstraint.class, errors);
        checkChildren(node.get("methods"), path + ".methods", OopMethodConstraint.class, errors);
        checkChildren(node.get("constructors"), path + ".constructors", OopConstructorConstraint.class, errors);
        for (String list : List.of("fields", "methods", "constructors", "requiredModifiers", "forbiddenModifiers",
                "expectedInterfaces")) {
            if (node.has(list) && node.get(list).isNull()) {
                errors.add(path + "." + list + ": must be an array (use [] instead of null)");
            }
        }
        if (rule.getMethods() == null) {
            return;
        }
        for (int i = 0; i < rule.getMethods().size(); i++) {
            OopMethodConstraint m = rule.getMethods().get(i);
            if (m == null || m.getName() == null || m.getName().isBlank()) {
                errors.add(path + ".methods[" + i + "].name: required");
            }
        }
    }

    private void checkScenario(JsonNode node, SocketScenarioConfig scenario, String path, List<String> errors) {
        if (scenario.getEffectiveTargetClass() == null || scenario.getEffectiveTargetClass().isBlank()) {
            errors.add(path + ".targetClass: required (or targetMainClass)");
        }
        if (scenario.getWeight() != null && scenario.getWeight() < 0) {
            errors.add(path + ".weight: must not be negative");
        }
        if (scenario.getConcurrentClients() < 1) {
            errors.add(path + ".concurrentClients: must be at least 1");
        }
        if (scenario.getTimeoutMs() <= 0) {
            errors.add(path + ".timeoutMs: must be positive");
        }
        checkChildren(node.get("steps"), path + ".steps", SocketStep.class, errors);
        boolean hasSteps = scenario.getSteps() != null && !scenario.getSteps().isEmpty();
        boolean hasPayload = scenario.getRequestPayload() != null || scenario.getExpectedResponsePayload() != null;
        if (!hasSteps && !hasPayload) {
            errors.add(path + ": needs steps[] or requestPayload/expectedResponsePayload");
        }
    }

    private void checkChildren(JsonNode array, String path, Class<?> type, List<String> errors) {
        if (array == null || !array.isArray()) {
            return; // kiểu sai đã được convertValue của đối tượng cha báo
        }
        for (int i = 0; i < array.size(); i++) {
            String childPath = path + "[" + i + "]";
            Object child = checkObject(array.get(i), childPath, type, errors);
            if (child instanceof OopFieldConstraint f) {
                checkModifiers(f.getRequiredModifiers(), childPath + ".requiredModifiers", errors);
                checkModifiers(f.getForbiddenModifiers(), childPath + ".forbiddenModifiers", errors);
            } else if (child instanceof OopMethodConstraint m) {
                checkModifiers(m.getRequiredModifiers(), childPath + ".requiredModifiers", errors);
                checkModifiers(m.getForbiddenModifiers(), childPath + ".forbiddenModifiers", errors);
            } else if (child instanceof OopConstructorConstraint c) {
                checkModifiers(c.getRequiredModifiers(), childPath + ".requiredModifiers", errors);
                checkModifiers(c.getForbiddenModifiers(), childPath + ".forbiddenModifiers", errors);
            }
        }
    }

    /** Kiểm tra key lạ, rồi ép kiểu bằng đúng model của judge để bắt sai kiểu/enum. */
    private <T> T checkObject(JsonNode node, String path, Class<T> type, List<String> errors) {
        if (node == null || !node.isObject()) {
            errors.add(path + ": must be an object");
            return null;
        }
        Set<String> known = propertiesOf(type);
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!known.contains(key)) {
                errors.add(path + "." + key + ": unknown key");
            }
        }
        try {
            return mapper.convertValue(node, type);
        } catch (IllegalArgumentException e) {
            errors.add(path + ": " + describe(e));
            return null;
        }
    }

    private Set<String> propertiesOf(Class<?> type) {
        return propertyCache.computeIfAbsent(type, t -> {
            JavaType javaType = mapper.constructType(t);
            BeanDescription description = mapper.getDeserializationConfig().introspect(javaType);
            Set<String> names = new TreeSet<>();
            for (BeanPropertyDefinition property : description.findProperties()) {
                if (property.couldDeserialize()) {
                    names.add(property.getName());
                }
            }
            return Set.copyOf(names);
        });
    }

    private static void checkModifiers(List<String> modifiers, String path, List<String> errors) {
        if (modifiers == null) {
            return;
        }
        for (String modifier : modifiers) {
            if (modifier == null || !JAVA_MODIFIERS.contains(modifier)) {
                errors.add(path + ": unknown modifier '" + modifier + "' (use lowercase, e.g. private, final)");
            }
        }
    }

    /** Thông báo ngắn: "Cannot deserialize value of type ... from String "X": not one of [..]". */
    private static String describe(IllegalArgumentException e) {
        String message = firstLine(e.getMessage());
        int at = message.indexOf(" at [Source");
        if (at > 0) {
            message = message.substring(0, at);
        }
        int chain = message.indexOf(" (through reference chain");
        if (chain > 0) {
            message = message.substring(0, chain);
        }
        return message.replace("jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.", "");
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "invalid value";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
