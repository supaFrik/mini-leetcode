package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class OopConstraintRule {
    private String ruleId;
    private String ruleName;
    private String description;
    @Builder.Default
    private double weight = 1.0;
    private String targetClassName;
    @Builder.Default
    private ExpectedClassType expectedType = ExpectedClassType.ANY;
    @Builder.Default
    private List<String> requiredModifiers = new ArrayList<>();
    @Builder.Default
    private List<String> forbiddenModifiers = new ArrayList<>();
    private String expectedSuperclass;
    @Builder.Default
    private List<String> expectedInterfaces = new ArrayList<>();
    @Builder.Default
    private Boolean enforceEncapsulation = false;
    @Builder.Default
    private List<OopFieldConstraint> fields = new ArrayList<>();
    @Builder.Default
    private List<OopMethodConstraint> methods = new ArrayList<>();
    @Builder.Default
    private List<OopConstructorConstraint> constructors = new ArrayList<>();
    private String customSuccessMessage;
    private String customFailureMessage;
}
