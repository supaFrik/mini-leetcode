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
}
