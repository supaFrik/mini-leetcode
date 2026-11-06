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
public class ExecutionReport {
    private Long submissionId;
    private String executionPhase;
    private Double totalScore;
    private String verdict;
    /**
     * Wall-clock thực tế (ms) của lần chạy mã nguồn OOP trong container.
     * {@code null} khi không đo được (VD: Docker không khả dụng).
     */
    private Integer runtimeMs;
    /**
     * Peak memory của cgroup (KB) của lần chạy mã nguồn OOP trong container.
     * {@code null} khi không đo được.
     */
    private Integer peakMemoryKb;
    private PhaseBreakdown phases;
    private String rawOutput;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhaseBreakdown {
        private CompilationPhase compilation;
        private OopInspectionPhase oopInspection;
        private UnitTestPhase unitTest;
        private SocketIntegrationPhase socketIntegration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompilationPhase {
        private boolean passed;
        private Long durationMs;
        private String errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OopInspectionPhase {
        private Double score;
        private Double weight;
        private Double weightedScore;
        @Builder.Default
        private List<OopRuleResult> rules = new ArrayList<>();
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnitTestPhase {
        private Double score;
        private Double weight;
        private Double weightedScore;
        private int passedCount;
        private int totalCount;
        @Builder.Default
        private List<UnitTestFailure> failures = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnitTestFailure {
        private String testClass;
        private String testMethod;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SocketIntegrationPhase {
        private Double score;
        private Double weight;
        private Double weightedScore;
        private String summary;
        @Builder.Default
        private List<SocketScenarioResult> scenarios = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SocketScenarioResult {
        private String scenarioName;
        private boolean passed;
        private SocketVerdict verdict;
        private Double score;
        private Long latencyMs;
        private String error;

        public static SocketScenarioResult success(String scenarioName, long latencyMs) {
            return SocketScenarioResult.builder()
                    .scenarioName(scenarioName)
                    .passed(true)
                    .verdict(SocketVerdict.SUCCESS)
                    .score(100.0)
                    .latencyMs(latencyMs)
                    .build();
        }

        public static SocketScenarioResult leak(String scenarioName, long latencyMs, String details) {
            return SocketScenarioResult.builder()
                    .scenarioName(scenarioName)
                    .passed(true)
                    .verdict(SocketVerdict.RESOURCE_LEAK)
                    .score(50.0)
                    .latencyMs(latencyMs)
                    .error("Resource leak: " + details)
                    .build();
        }

        public static SocketScenarioResult failure(String scenarioName, SocketVerdict verdict, String error) {
            return SocketScenarioResult.builder()
                    .scenarioName(scenarioName)
                    .passed(false)
                    .verdict(verdict)
                    .score(verdict != null ? verdict.getBaseScore() : 0.0)
                    .error(error)
                    .build();
        }
    }
}
