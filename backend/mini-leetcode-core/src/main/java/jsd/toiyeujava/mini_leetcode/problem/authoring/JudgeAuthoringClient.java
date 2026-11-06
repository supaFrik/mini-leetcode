package jsd.toiyeujava.mini_leetcode.problem.authoring;

import java.util.List;

/**
 * Gọi dịch vụ soạn bài nội bộ của judge worker ({@code /internal/authoring/**}).
 * Mã nguồn lời giải mẫu chỉ chạy ở judge (Docker sandbox), không bao giờ ở API.
 */
public interface JudgeAuthoringClient {

    record SourceFile(String path, String content) {
    }

    record CaseInput(String input, String expectedOutput) {
    }

    record ValidationResult(boolean valid, List<String> errors, int ruleCount, int socketScenarioCount,
                            double totalRuleWeight) {
    }

    record CaseRun(String stdout, String stderr, Integer exitCode, Integer runtimeMs,
                   boolean timedOut, boolean oomKilled, boolean truncated, Boolean matchesExpected) {
        public boolean succeeded() {
            return !timedOut && !oomKilled && !truncated && exitCode != null && exitCode == 0;
        }
    }

    record RunResult(boolean compiled, String compileOutput, String mainClass, String error, List<CaseRun> cases) {
    }

    /** @throws jsd.toiyeujava.mini_leetcode.exception.JudgeUnavailableException khi judge không phản hồi */
    ValidationResult validateGradingConfig(String gradingConfig);

    /** @throws jsd.toiyeujava.mini_leetcode.exception.JudgeUnavailableException khi judge không phản hồi / bận */
    RunResult runReference(List<SourceFile> files, List<CaseInput> inputs, long timeLimitMs, int memoryLimitMb);
}
