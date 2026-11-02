package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OopGrindingInspector {

    private final ObjectMapper objectMapper;

    public OopInspectionReport inspect(Path classesDir, String rulesJson) throws IOException {
        return inspect(classesDir, rulesJson, 0.40);
    }

    public OopInspectionReport inspect(Path classesDir, String rulesJson, double phaseWeight) throws IOException {
        List<OopConstraintRule> rules = parseRulesJson(rulesJson);
        return inspect(classesDir, rules, phaseWeight);
    }

    public OopInspectionReport inspect(Path classesDir, List<OopConstraintRule> rules) {
        return inspect(classesDir, rules, 0.40);
    }

    public OopInspectionReport inspect(Path classesDir, List<OopConstraintRule> rules, double phaseWeight) {
        if (rules == null || rules.isEmpty()) {
            return OopInspectionReport.builder()
                    .passed(true)
                    .score(100.0)
                    .weight(phaseWeight)
                    .weightedScore(Math.round(100.0 * phaseWeight * 10.0) / 10.0)
                    .rules(Collections.emptyList())
                    .summary("No OOP constraint rules configured")
                    .build();
        }

        if (classesDir == null || !Files.exists(classesDir)) {
            List<OopRuleResult> failedRules = rules.stream()
                    .map(r -> OopRuleResult.failed(
                            r.getRuleName() != null ? r.getRuleName() : r.getTargetClassName(),
                            r.getTargetClassName(),
                            "Classes directory not found: " + classesDir,
                            r.getWeight()))
                    .toList();
            return buildReport(failedRules, phaseWeight);
        }

        List<OopRuleResult> results = new ArrayList<>();

        try {
            URL[] urls = new URL[]{classesDir.toUri().toURL()};
            // PlatformClassLoader as parent isolates student code from host/judge classpath
            try (URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                for (OopConstraintRule rule : rules) {
                    OopRuleResult result = evaluateRule(rule, classLoader, classesDir);
                    results.add(result);
                }
            }
        } catch (Throwable t) {
            log.error("Fatal error during OOP inspection for classesDir: {}", classesDir, t);
            return buildReport(Collections.singletonList(
                    OopRuleResult.failed("FATAL_INSPECTION_ERROR", "ALL", "Fatal error: " + t.getMessage(), 1.0)), phaseWeight);
        }

        return buildReport(results, phaseWeight);
    }

    private OopRuleResult evaluateRule(OopConstraintRule rule, ClassLoader classLoader, Path classesDir) {
        String ruleName = rule.getRuleName() != null ? rule.getRuleName() : rule.getTargetClassName();
        String targetName = rule.getTargetClassName();

        if (targetName == null || targetName.isBlank()) {
            return OopRuleResult.failed(ruleName, "UNKNOWN", "Rule missing targetClassName", rule.getWeight());
        }

        try {
            Class<?> clazz = loadTargetClass(targetName, classLoader, classesDir);
            List<String> violations = new ArrayList<>();

            // 1. Verify Class Type
            verifyClassType(clazz, rule.getExpectedType(), violations);

            // 2. Verify Modifiers
            List<String> modViolations = ModifierHelper.checkModifiers(
                    clazz.getModifiers(),
                    rule.getRequiredModifiers(),
                    rule.getForbiddenModifiers(),
                    "Class " + clazz.getSimpleName()
            );
            violations.addAll(modViolations);

            // 3. Verify Superclass
            if (rule.getExpectedSuperclass() != null && !rule.getExpectedSuperclass().isBlank()) {
                verifySuperclass(clazz, rule.getExpectedSuperclass(), violations);
            }

            // 4. Verify Interfaces
            if (rule.getExpectedInterfaces() != null && !rule.getExpectedInterfaces().isEmpty()) {
                verifyInterfaces(clazz, rule.getExpectedInterfaces(), violations);
            }

            // 5. Verify Encapsulation
            if (Boolean.TRUE.equals(rule.getEnforceEncapsulation())) {
                verifyEncapsulation(clazz, violations);
            }

            // 6. Verify Field Constraints
            if (rule.getFields() != null) {
                for (OopFieldConstraint fieldRule : rule.getFields()) {
                    verifyFieldConstraint(clazz, fieldRule, violations);
                }
            }

            // 7. Verify Method Constraints
            if (rule.getMethods() != null) {
                for (OopMethodConstraint methodRule : rule.getMethods()) {
                    verifyMethodConstraint(clazz, methodRule, violations);
                }
            }

            // 8. Verify Constructor Constraints
            if (rule.getConstructors() != null) {
                for (OopConstructorConstraint ctorRule : rule.getConstructors()) {
                    verifyConstructorConstraint(clazz, ctorRule, violations);
                }
            }

            if (violations.isEmpty()) {
                String successMsg = rule.getCustomSuccessMessage() != null && !rule.getCustomSuccessMessage().isBlank()
                        ? rule.getCustomSuccessMessage()
                        : "Class " + targetName + " meets all specified OOP constraints";
                return OopRuleResult.success(ruleName, targetName, successMsg, rule.getWeight());
            } else {
                String failMsg = rule.getCustomFailureMessage() != null && !rule.getCustomFailureMessage().isBlank()
                        ? rule.getCustomFailureMessage() + ": " + String.join("; ", violations)
                        : String.join("; ", violations);
                OopRuleResult res = OopRuleResult.failed(ruleName, targetName, failMsg, rule.getWeight());
                res.setDetails(violations);
                return res;
            }

        } catch (ClassNotFoundException e) {
            return OopRuleResult.failed(ruleName, targetName,
                    "Required class '" + targetName + "' was not found in compiled bytecode", rule.getWeight());
        } catch (LinkageError e) {
            return OopRuleResult.failed(ruleName, targetName,
                    "Linkage error loading class '" + targetName + "': " + e.getMessage(), rule.getWeight());
        } catch (Throwable t) {
            return OopRuleResult.failed(ruleName, targetName,
                    "Error inspecting class '" + targetName + "': " + t.getMessage(), rule.getWeight());
        }
    }

    private Class<?> loadTargetClass(String targetName, ClassLoader classLoader, Path classesDir) throws ClassNotFoundException {
        // initialize = false prevents <clinit> execution!
        try {
            return Class.forName(targetName, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            // Attempt resolving by simple name or relative path if class is in a package
            String resolvedFqcn = findClassFqcn(classesDir, targetName);
            if (resolvedFqcn != null) {
                return Class.forName(resolvedFqcn, false, classLoader);
            }
            throw ignored;
        }
    }

    private String findClassFqcn(Path classesDir, String targetName) {
        String simpleName = targetName.contains(".")
                ? targetName.substring(targetName.lastIndexOf('.') + 1)
                : targetName;

        try (Stream<Path> stream = Files.walk(classesDir)) {
            return stream.filter(p -> p.getFileName().toString().equals(simpleName + ".class"))
                    .findFirst()
                    .map(p -> {
                        Path rel = classesDir.relativize(p);
                        String pathStr = rel.toString()
                                .replace('/', '.')
                                .replace('\\', '.');
                        if (pathStr.endsWith(".class")) {
                            pathStr = pathStr.substring(0, pathStr.length() - 6);
                        }
                        return pathStr;
                    })
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void verifyClassType(Class<?> clazz, ExpectedClassType expected, List<String> violations) {
        if (expected == null || expected == ExpectedClassType.ANY) return;
        switch (expected) {
            case ABSTRACT_CLASS -> {
                if (clazz.isInterface() || !Modifier.isAbstract(clazz.getModifiers())) {
                    violations.add("Expected ABSTRACT_CLASS, but found " + getActualTypeDesc(clazz));
                }
            }
            case INTERFACE -> {
                if (!clazz.isInterface()) {
                    violations.add("Expected INTERFACE, but found " + getActualTypeDesc(clazz));
                }
            }
            case CLASS -> {
                if (clazz.isInterface() || clazz.isEnum() || clazz.isRecord() || Modifier.isAbstract(clazz.getModifiers())) {
                    violations.add("Expected concrete CLASS, but found " + getActualTypeDesc(clazz));
                }
            }
            case ENUM -> {
                if (!clazz.isEnum()) {
                    violations.add("Expected ENUM, but found " + getActualTypeDesc(clazz));
                }
            }
            case RECORD -> {
                if (!clazz.isRecord()) {
                    violations.add("Expected RECORD, but found " + getActualTypeDesc(clazz));
                }
            }
        }
    }

    private String getActualTypeDesc(Class<?> clazz) {
        if (clazz.isInterface()) return "INTERFACE";
        if (clazz.isRecord()) return "RECORD";
        if (clazz.isEnum()) return "ENUM";
        if (Modifier.isAbstract(clazz.getModifiers())) return "ABSTRACT_CLASS";
        return "CLASS";
    }

    private void verifySuperclass(Class<?> clazz, String expectedSuperclass, List<String> violations) {
        Class<?> actualSuperclass = clazz.getSuperclass();
        if (actualSuperclass == null || !ModifierHelper.matchesType(actualSuperclass, expectedSuperclass)) {
            String actualName = actualSuperclass != null ? actualSuperclass.getName() : "none";
            violations.add("Expected superclass '" + expectedSuperclass + "', but found '" + actualName + "'");
        }
    }

    private void verifyInterfaces(Class<?> clazz, List<String> expectedInterfaces, List<String> violations) {
        Class<?>[] actualIfaces = clazz.getInterfaces();
        for (String expectedIface : expectedInterfaces) {
            boolean matched = false;
            for (Class<?> actual : actualIfaces) {
                if (ModifierHelper.matchesType(actual, expectedIface)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                violations.add("Class does not implement expected interface '" + expectedIface + "'");
            }
        }
    }

    private void verifyEncapsulation(Class<?> clazz, List<String> violations) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic() || field.getName().startsWith("$")) continue;
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!Modifier.isPrivate(field.getModifiers())) {
                violations.add("Field '" + field.getName() + "' is not private (encapsulation violation: "
                        + Modifier.toString(field.getModifiers()) + ")");
            }
        }
    }

    private void verifyFieldConstraint(Class<?> clazz, OopFieldConstraint fieldRule, List<String> violations) {
        Field[] fields = clazz.getDeclaredFields();

        if (fieldRule.isMatchAllFields() || "*".equals(fieldRule.getName())) {
            for (Field field : fields) {
                if (fieldRule.isIgnoreSynthetic() && (field.isSynthetic() || field.getName().startsWith("$"))) {
                    continue;
                }
                if (fieldRule.isIgnoreStatic() && Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (fieldRule.getType() != null && !ModifierHelper.matchesType(field.getType(), fieldRule.getType())) {
                    violations.add("Field '" + field.getName() + "' expected type " + fieldRule.getType() + " but found " + field.getType().getSimpleName());
                }
                violations.addAll(ModifierHelper.checkModifiers(
                        field.getModifiers(),
                        fieldRule.getRequiredModifiers(),
                        fieldRule.getForbiddenModifiers(),
                        "Field '" + field.getName() + "'"
                ));
            }
            return;
        }

        String fieldName = fieldRule.getName();
        if (fieldName == null || fieldName.isBlank()) return;

        Field targetField = null;
        for (Field f : fields) {
            if (f.getName().equals(fieldName)) {
                targetField = f;
                break;
            }
        }

        if (targetField == null) {
            violations.add("Field '" + fieldName + "' not found in class " + clazz.getSimpleName());
            return;
        }

        if (fieldRule.getType() != null && !ModifierHelper.matchesType(targetField.getType(), fieldRule.getType())) {
            violations.add("Field '" + fieldName + "' expected type " + fieldRule.getType() + " but found " + targetField.getType().getSimpleName());
        }

        violations.addAll(ModifierHelper.checkModifiers(
                targetField.getModifiers(),
                fieldRule.getRequiredModifiers(),
                fieldRule.getForbiddenModifiers(),
                "Field '" + fieldName + "'"
        ));
    }

    private void verifyMethodConstraint(Class<?> clazz, OopMethodConstraint methodRule, List<String> violations) {
        String methodName = methodRule.getName();
        if (methodName == null || methodName.isBlank()) return;

        List<String> expectedParams = methodRule.getParameterTypes() != null ? methodRule.getParameterTypes() : Collections.emptyList();
        Method[] methods = clazz.getDeclaredMethods();

        Method matchedMethod = null;
        for (Method m : methods) {
            if (methodRule.isIgnoreSynthetic() && (m.isSynthetic() || m.isBridge() || m.getName().startsWith("$"))) {
                continue;
            }
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] actualParams = m.getParameterTypes();
            if (actualParams.length != expectedParams.size()) {
                continue;
            }
            boolean paramsMatch = true;
            for (int i = 0; i < actualParams.length; i++) {
                if (!ModifierHelper.matchesType(actualParams[i], expectedParams.get(i))) {
                    paramsMatch = false;
                    break;
                }
            }
            if (paramsMatch) {
                matchedMethod = m;
                break;
            }
        }

        if (matchedMethod == null) {
            violations.add("Method '" + methodName + "(" + String.join(", ", expectedParams) + ")' not found in class " + clazz.getSimpleName());
            return;
        }

        if (methodRule.getReturnType() != null && !ModifierHelper.matchesType(matchedMethod.getReturnType(), methodRule.getReturnType())) {
            violations.add("Method '" + methodName + "' expected return type " + methodRule.getReturnType() + " but found " + matchedMethod.getReturnType().getSimpleName());
        }

        violations.addAll(ModifierHelper.checkModifiers(
                matchedMethod.getModifiers(),
                methodRule.getRequiredModifiers(),
                methodRule.getForbiddenModifiers(),
                "Method '" + methodName + "'"
        ));
    }

    private void verifyConstructorConstraint(Class<?> clazz, OopConstructorConstraint ctorRule, List<String> violations) {
        List<String> expectedParams = ctorRule.getParameterTypes() != null ? ctorRule.getParameterTypes() : Collections.emptyList();
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        Constructor<?> matchedCtor = null;
        for (Constructor<?> ctor : constructors) {
            Class<?>[] actualParams = ctor.getParameterTypes();
            if (actualParams.length != expectedParams.size()) {
                continue;
            }
            boolean paramsMatch = true;
            for (int i = 0; i < actualParams.length; i++) {
                if (!ModifierHelper.matchesType(actualParams[i], expectedParams.get(i))) {
                    paramsMatch = false;
                    break;
                }
            }
            if (paramsMatch) {
                matchedCtor = ctor;
                break;
            }
        }

        if (matchedCtor == null) {
            violations.add("Constructor " + clazz.getSimpleName() + "(" + String.join(", ", expectedParams) + ") not found");
            return;
        }

        violations.addAll(ModifierHelper.checkModifiers(
                matchedCtor.getModifiers(),
                ctorRule.getRequiredModifiers(),
                ctorRule.getForbiddenModifiers(),
                "Constructor " + clazz.getSimpleName() + "(" + String.join(", ", expectedParams) + ")"
        ));
    }

    private OopInspectionReport buildReport(List<OopRuleResult> results, double phaseWeight) {
        double totalWeight = results.stream().mapToDouble(r -> r.getWeight() != null ? r.getWeight() : 1.0).sum();
        double passedWeight = results.stream()
                .filter(OopRuleResult::isPassed)
                .mapToDouble(r -> r.getWeight() != null ? r.getWeight() : 1.0)
                .sum();

        double score = totalWeight <= 0.0 ? 100.0 : Math.round((passedWeight / totalWeight) * 1000.0) / 10.0;
        double weightedScore = Math.round(score * phaseWeight * 10.0) / 10.0;
        boolean allPassed = results.stream().allMatch(OopRuleResult::isPassed);

        return OopInspectionReport.builder()
                .passed(allPassed)
                .score(score)
                .weight(phaseWeight)
                .weightedScore(weightedScore)
                .rules(results)
                .summary(String.format("%d/%d rules passed (score: %.1f)",
                        results.stream().filter(OopRuleResult::isPassed).count(), results.size(), score))
                .build();
    }

    private List<OopConstraintRule> parseRulesJson(String rulesJson) throws IOException {
        if (rulesJson == null || rulesJson.isBlank()) return Collections.emptyList();
        String trimmed = rulesJson.trim();
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(trimmed, new TypeReference<List<OopConstraintRule>>() {});
        } else if (trimmed.startsWith("{")) {
            Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
            if (map.containsKey("rules")) {
                String rulesSubJson = objectMapper.writeValueAsString(map.get("rules"));
                return objectMapper.readValue(rulesSubJson, new TypeReference<List<OopConstraintRule>>() {});
            } else {
                return Collections.singletonList(objectMapper.readValue(trimmed, OopConstraintRule.class));
            }
        }
        return Collections.emptyList();
    }
}
