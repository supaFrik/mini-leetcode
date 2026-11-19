package jsd.toiyeujava.mini_leetcode.judge.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GradingConfigValidatorTest {

    private final GradingConfigValidator validator = new GradingConfigValidator(new ObjectMapper());

    /** Các cấu hình thật đang dùng trong repo phải hợp lệ. */
    @ParameterizedTest
    @ValueSource(strings = {"mini-bank-console", "shape-hierarchy", "socket-echo-server"})
    void acceptsShippedGradingConfigs(String slug) throws Exception {
        Path file = Path.of("..", "tools", "problems", "grading", slug + ".json");
        GradingConfigValidator.Result result = validator.validate(Files.readString(file));
        assertThat(result.errors()).isEmpty();
        assertThat(result.ruleCount() + result.socketScenarioCount()).isPositive();
    }

    @Test
    void acceptsMinimalRule() {
        var result = validator.validate("""
                {"rules": [{"targetClassName": "Account", "expectedType": "CLASS", "weight": 2,
                  "requiredModifiers": ["public"],
                  "methods": [{"name": "deposit", "returnType": "void", "parameterTypes": ["long"]}]}]}
                """);
        assertThat(result.valid()).isTrue();
        assertThat(result.ruleCount()).isEqualTo(1);
        assertThat(result.totalRuleWeight()).isEqualTo(2.0);
    }

    @Test
    void reportsUnknownKeysWithPath() {
        var result = validator.validate("""
                {"rules": [{"targetClassName": "A", "methods": [{"name": "m", "retunType": "int"}]}], "rule": []}
                """);
        assertThat(result.errors()).contains(
                "rule: unknown key (allowed: rules, socketScenarios, output)",
                "rules[0].methods[0].retunType: unknown key");
    }

    @Test
    void reportsBadEnumAndMissingTarget() {
        var result = validator.validate("""
                {"rules": [{"expectedType": "KLASS"}]}
                """);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).startsWith("rules[0]:").contains("KLASS"));
    }

    @Test
    void reportsMissingTargetClassName() {
        var result = validator.validate("""
                {"rules": [{"expectedType": "INTERFACE"}]}
                """);
        assertThat(result.errors()).containsExactly("rules[0].targetClassName: required");
    }

    @Test
    void reportsUnknownModifier() {
        var result = validator.validate("""
                {"rules": [{"targetClassName": "A", "fields": [{"name": "x", "requiredModifiers": ["Private"]}]}]}
                """);
        assertThat(result.errors()).containsExactly(
                "rules[0].fields[0].requiredModifiers: unknown modifier 'Private' (use lowercase, e.g. private, final)");
    }

    @Test
    void reportsInvalidJsonAndEmptyConfig() {
        assertThat(validator.validate("{not json").errors()).singleElement().asString().startsWith("Not valid JSON");
        assertThat(validator.validate("[]").errors()).singleElement().asString().startsWith("Root must be");
        assertThat(validator.validate("{}").errors()).singleElement().asString().contains("no rules");
        assertThat(validator.validate("  ").errors()).containsExactly("Grading configuration is empty");
    }

    @Test
    void acceptsOutputOnlyConfig() {
        // Bài OOP chấm hoàn toàn bằng testcase stdin/stdout: chỉ cần tuỳ chọn so output.
        var result = validator.validate("{\"output\": {\"mode\": \"normalized\", \"ignoreCase\": false}}");
        assertThat(result.errors()).isEmpty();
        assertThat(result.ruleCount()).isZero();
    }

    @Test
    void acceptsAndChecksOutputPolicy() {
        String rule = "\"rules\": [{\"targetClassName\": \"Account\"}]";
        assertThat(validator.validate("{" + rule + ", \"output\": {\"mode\": \"normalized\", \"ignoreCase\": true}}").errors())
                .isEmpty();
        assertThat(validator.validate("{" + rule + ", \"output\": {\"mode\": \"fuzzy\", \"ignoreCase\": \"yes\", \"trim\": 1}}").errors())
                .containsExactlyInAnyOrder(
                        "output.trim: unknown key (allowed: mode, ignoreCase)",
                        "output.mode: must be \"exact\" or \"normalized\"",
                        "output.ignoreCase: must be true or false");
        assertThat(validator.validate("{" + rule + ", \"output\": \"normalized\"}").errors())
                .singleElement().asString().startsWith("output: must be an object");
    }

    @Test
    void socketScenarioNeedsTargetAndSteps() {
        var result = validator.validate("""
                {"socketScenarios": [{"scenarioName": "echo", "role": "CLIENT"}]}
                """);
        assertThat(result.errors()).contains(
                "socketScenarios[0].targetClass: required (or targetMainClass)",
                "socketScenarios[0]: needs steps[] or requestPayload/expectedResponsePayload");
    }

    @Test
    void acceptsPresetRulesWithWildcardAndNewProperties() {
        var result = validator.validate("""
                {
                  "rules": [
                    {
                      "ruleId": "preset-encapsulation",
                      "ruleName": "Encapsulation",
                      "targetClassName": "*",
                      "enforceEncapsulation": true,
                      "weight": 1.0
                    },
                    {
                      "ruleId": "preset-polymorphism",
                      "ruleName": "Polymorphism",
                      "targetClassName": "*",
                      "expectedType": "ABSTRACT_CLASS_OR_INTERFACE",
                      "weight": 1.0
                    },
                    {
                      "ruleId": "preset-anti-monolithic",
                      "ruleName": "Anti-Monolithic",
                      "targetClassName": "*",
                      "minClassCount": 2,
                      "disallowMonolithicMain": true,
                      "weight": 1.0
                    }
                  ]
                }
                """);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.ruleCount()).isEqualTo(3);
    }

    @Test
    void acceptsAllFiveStrictOopRules() {
        var result = validator.validate("""
                {
                  "rules": [
                    {
                      "ruleId": "preset-strict-immutability",
                      "ruleName": "Strict Immutability",
                      "targetClassName": "Money",
                      "requireFinalFields": true,
                      "weight": 1.5
                    },
                    {
                      "ruleId": "preset-clean-code-limits",
                      "ruleName": "Clean Code Limits",
                      "targetClassName": "*",
                      "maxMethodsPerClass": 12,
                      "maxLinesPerMethod": 30,
                      "weight": 1.0
                    },
                    {
                      "ruleId": "preset-require-override",
                      "ruleName": "Require Override",
                      "targetClassName": "*",
                      "requireOverrideAnnotation": true,
                      "requireAnnotations": ["@Override"],
                      "weight": 1.0
                    },
                    {
                      "ruleId": "preset-anti-anemic",
                      "ruleName": "Anti-Anemic Domain Model",
                      "targetClassName": "Account",
                      "antiAnemic": true,
                      "weight": 2.0
                    },
                    {
                      "ruleId": "preset-loose-coupling",
                      "ruleName": "Loose Coupling",
                      "targetClassName": "*",
                      "maxCbo": 6,
                      "weight": 1.0
                    }
                  ]
                }
                """);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.ruleCount()).isEqualTo(5);
        assertThat(result.totalRuleWeight()).isEqualTo(6.5);
    }

    @Test
    void rejectsInvalidBoundsForStrictRules() {
        var result = validator.validate("""
                {
                  "rules": [
                    {
                      "targetClassName": "A",
                      "maxMethodsPerClass": 0,
                      "maxLinesPerMethod": -5,
                      "maxCbo": -1
                    }
                  ]
                }
                """);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactlyInAnyOrder(
                "rules[0].maxMethodsPerClass: must be at least 1",
                "rules[0].maxLinesPerMethod: must be at least 1",
                "rules[0].maxCbo: must not be negative"
        );
    }

    @Test
    void rejectsNullRequireAnnotations() {
        var result = validator.validate("""
                {
                  "rules": [
                    {
                      "targetClassName": "A",
                      "requireAnnotations": null
                    }
                  ]
                }
                """);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("rules[0].requireAnnotations: must be an array (use [] instead of null)");
    }
}
