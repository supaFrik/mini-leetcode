package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Phần plugin cần từ {@code SubmissionResponse} của backend (GET /api/v1/submissions/{id}).
 * {@code status}: PENDING / JUDGING / COMPLETED / FAILED; {@code verdict}: AC, WA, TLE, MLE, CE, RE, SV.
 * {@code scoreBreakdown} là JSON dạng chuỗi (điểm từng phần của bài OOP), có thể null.
 */
public record SubmissionDto(
        Long id,
        Long problemId,
        String status,
        String verdict,
        Double score,
        Integer passTestCases,
        Integer totalTestCases,
        String errorMessage,
        String scoreBreakdown
) {
    public boolean isFinished() {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    public boolean isAccepted() {
        return "AC".equals(verdict);
    }
}
