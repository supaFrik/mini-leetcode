package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OopRuleResult {
    private String ruleName;
    private String targetClass;
    private boolean passed;
    private String message;
    private Double weight;
    private Double earnedScore;
    @Builder.Default
    private List<String> details = new ArrayList<>();

    public static OopRuleResult success(String ruleName, String targetClass, String message, double weight) {
        return OopRuleResult.builder()
                .ruleName(ruleName)
                .targetClass(targetClass)
                .passed(true)
                .message(message)
                .weight(weight)
                .earnedScore(weight)
                .build();
    }

    public static OopRuleResult failed(String ruleName, String targetClass, String message, double weight) {
        return OopRuleResult.builder()
                .ruleName(ruleName)
                .targetClass(targetClass)
                .passed(false)
                .message(message)
                .weight(weight)
                .earnedScore(0.0)
                .build();
    }
}
