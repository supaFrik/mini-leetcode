package jsd.toiyeujava.mini_leetcode.submission.dto;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;

import java.time.LocalDateTime;

public record SubmissionResponse(
        Long id,
        Long problemId,
        String problemTitle,
        Long contestId,
        Long userId,
        String username,
        SubmissionLanguage language,
        SubmissionStatus status,
        SubmissionVerdict verdict,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        Integer passTestCases,
        Integer totalTestCases,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SubmissionResponse fromEntity(Submission s) {
        if (s == null) {
            return null;
        }
        return new SubmissionResponse(
                s.getId(),
                s.getProblem() != null ? s.getProblem().getId() : null,
                s.getProblem() != null ? s.getProblem().getTitle() : null,
                s.getContestId(),
                s.getUser() != null ? s.getUser().getId() : null,
                s.getUser() != null ? s.getUser().getUsername() : null,
                s.getLanguage(),
                s.getStatus(),
                s.getVerdict(),
                s.getExecutionTimeMs(),
                s.getMemoryUsedKb(),
                s.getPassTestCases(),
                s.getTotalTestCases(),
                s.getErrorMessage(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
