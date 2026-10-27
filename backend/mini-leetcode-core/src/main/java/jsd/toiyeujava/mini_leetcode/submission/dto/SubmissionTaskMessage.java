package jsd.toiyeujava.mini_leetcode.submission.dto;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

public record SubmissionTaskMessage(
        Long submissionId,
        Long problemId,
        Long userId,
        SubmissionLanguage language
) {
}
