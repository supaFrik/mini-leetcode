package jsd.toiyeujava.mini_leetcode.problem.authoring;

import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;

import java.util.List;

/** DTO của API soạn bài ({@code /api/v1/admin/problems/{id}/...}). Chỉ INSTRUCTOR/ADMIN thấy. */
public final class AuthoringDtos {

    private AuthoringDtos() {
    }

    public record SourceFile(String path, String content) {
    }

    /**
     * @param gradingFrozen   {@code true} khi bài PUBLISHED: không sửa grading_config / testcase được
     * @param referenceSolution danh sách rỗng khi chưa có lời giải mẫu
     */
    public record AuthoringResponse(Long problemId, ProblemStatus status, ProblemType problemType,
                                    String gradingConfig, List<SourceFile> referenceSolution,
                                    boolean gradingFrozen, int timeLimitMs, int memoryLimitMb) {
    }

    /** {@code gradingConfig} null/rỗng ⇒ xoá cấu hình. */
    public record UpdateGradingConfigRequest(String gradingConfig) {
    }

    public record GradingValidationResponse(boolean valid, List<String> errors, int ruleCount,
                                            int socketScenarioCount, double totalRuleWeight) {
    }

    /** Danh sách rỗng ⇒ xoá lời giải mẫu. */
    public record UpdateReferenceSolutionRequest(List<SourceFile> files) {
    }

    /**
     * Chạy lời giải mẫu đã lưu.
     *
     * <ul>
     *   <li>{@code inputs} khác rỗng: chạy trên các stdin tuỳ ý (soạn testcase mới), không ghi gì.</li>
     *   <li>ngược lại: chạy trên testcase của bài ({@code testCaseIds} null = tất cả) và so với output hiện tại;
     *       {@code apply = true} ghi output mới vào các testcase bị khác (DIFF).</li>
     * </ul>
     */
    public record ReferenceRunRequest(List<Long> testCaseIds, List<String> inputs, Boolean apply) {
    }

    /** Kết quả từng case. {@code testCaseId} null với stdin tuỳ ý. */
    public record CaseResult(Long testCaseId, Integer orderIndex, Boolean hidden, CaseStatus status,
                             String stdout, String stderr, Integer exitCode, Integer runtimeMs, boolean applied) {
    }

    public enum CaseStatus {
        /** Output trùng output kỳ vọng hiện tại. */
        MATCH,
        /** Chạy được nhưng khác output kỳ vọng hiện tại (Apply sẽ ghi đè). */
        DIFF,
        /** Chạy được (stdin tuỳ ý, hoặc output kỳ vọng quá lớn để so). */
        OK,
        TIME_LIMIT,
        MEMORY_LIMIT,
        RUNTIME_ERROR,
        OUTPUT_LIMIT
    }

    public record ReferenceRunResponse(boolean compiled, String compileOutput, String mainClass, String error,
                                       List<CaseResult> cases, int appliedCount) {
    }
}
