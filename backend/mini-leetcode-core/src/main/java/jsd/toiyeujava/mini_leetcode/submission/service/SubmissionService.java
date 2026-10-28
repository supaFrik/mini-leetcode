package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubmissionService {

    Submission createSubmission(Long problemId, Long userId, SubmitCodeRequest request);

    Submission getSubmissionById(Long id);

    SubmissionResponse getSubmissionResponseById(Long id);

    String getSubmissionCode(Long id);

    Page<SubmissionResponse> getUserSubmissions(Long userId, Pageable pageable);

    Page<SubmissionResponse> getProblemSubmissions(Long problemId, Pageable pageable);

    Submission updateSubmissionVerdict(
            Long submissionId,
            SubmissionStatus status,
            SubmissionVerdict verdict,
            Integer executionTimeMs,
            Integer memoryUsedKb,
            Integer passTestCases,
            Integer totalTestCases,
            String errorMessage
    );

    Submission failSubmission(Long submissionId, String errorMessage);
}

