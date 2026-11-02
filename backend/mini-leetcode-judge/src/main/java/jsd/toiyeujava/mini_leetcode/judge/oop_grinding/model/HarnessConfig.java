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
public class HarnessConfig {
    private Long submissionId;
    private Long problemId;
    private String studentClassesDir;
    private String testClassesDir;
    private String outputReportPath;
    @Builder.Default
    private TimeoutBudget timeoutBudget = new TimeoutBudget();
    @Builder.Default
    private OopInspectionConfig oopInspection = new OopInspectionConfig();
    @Builder.Default
    private UnitTestConfig unitTest = new UnitTestConfig();
    @Builder.Default
    private SocketIntegrationConfig socketIntegration = new SocketIntegrationConfig();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeoutBudget {
        @Builder.Default
        private int containerTimeoutSeconds = 30;
        @Builder.Default
        private int unitTestTimeoutSeconds = 2;
        @Builder.Default
        private int socketScenarioTimeoutSeconds = 3;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OopInspectionConfig {
        @Builder.Default
        private boolean enabled = true;
        @Builder.Default
        private double weight = 0.40;
        @Builder.Default
        private List<OopConstraintRule> rules = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UnitTestConfig {
        @Builder.Default
        private boolean enabled = true;
        @Builder.Default
        private double weight = 0.60;
        @Builder.Default
        private List<String> testClassNames = new ArrayList<>();
        @Builder.Default
        private int timeoutSecondsPerTest = 2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SocketIntegrationConfig {
        @Builder.Default
        private boolean enabled = false;
        @Builder.Default
        private double weight = 0.0;
        @Builder.Default
        private List<SocketScenarioConfig> scenarios = new ArrayList<>();
    }
}
