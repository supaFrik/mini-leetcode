package jsd.toiyeujava.mini_leetcode.submission.dto;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

public record SubmissionTaskMessage(
        Long submissionId,
        Long problemId,
        Long userId,
        SubmissionLanguage language,
        Boolean isQuickRun
) {
    public SubmissionTaskMessage {
        if (isQuickRun == null) {
            isQuickRun = false;
        }
    }

    public SubmissionTaskMessage(Long submissionId, Long problemId, Long userId, SubmissionLanguage language) {
        this(submissionId, problemId, userId, language, false);
    }
}
