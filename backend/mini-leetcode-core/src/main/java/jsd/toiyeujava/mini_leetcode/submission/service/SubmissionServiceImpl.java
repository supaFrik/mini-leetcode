package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ContestRepository contestRepository;
    private final ContestParticipantRepository contestParticipantRepository;

    @Override
    @Transactional
    public Submission createSubmission(Long problemId, Long userId, SubmitCodeRequest request) {
        if (problemId == null) {
            throw new ResourceNotFoundException("Problem not found with id: null");
        }
        if (userId == null) {
            throw new ResourceNotFoundException("User not found with id: null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Submit code request must not be null");
        }
        if (request.language() == null) {
            throw new IllegalArgumentException("Language is required");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Code must not be blank");
        }

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // If submitted under an active contest (Exam mode), validate contest and participant status
        if (request.contestId() != null) {
            Contest contest = contestRepository.findById(request.contestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + request.contestId()));

            if (contest.getStatus() != ContestStatus.RUNNING) {
                throw new AccessDeniedException("Contest is not currently active (status: " + contest.getStatus() + ")");
            }

            ContestParticipant participant = contestParticipantRepository.findByContestIdAndUserId(request.contestId(), userId)
                    .orElseThrow(() -> new AccessDeniedException("User is not registered for contest id: " + request.contestId()));

            if (participant.getStatus() == ParticipantStatus.DISQUALIFIED) {
                throw new AccessDeniedException("User has been disqualified from this contest due to proctoring violations");
            }
            if (participant.getStatus() == ParticipantStatus.SUBMITTED) {
                throw new AccessDeniedException("User has already finalized their contest submission");
            }
            if (participant.getStatus() == ParticipantStatus.REGISTERED) {
                participant.setStatus(ParticipantStatus.IN_PROGRESS);
                contestParticipantRepository.save(participant);
            }
        }

        String extension = request.language() == SubmissionLanguage.JAVA ? ".java"
                : (request.language() == SubmissionLanguage.PYTHON ? ".py" : ".cpp");
        String objectName = "submissions/" + problemId + "/" + userId + "/" + UUID.randomUUID() + extension;

        byte[] codeBytes = request.code().getBytes(StandardCharsets.UTF_8);
        String codeStorageUri = storageService.uploadFile(
                objectName,
                new ByteArrayInputStream(codeBytes),
                codeBytes.length,
                "text/plain"
        );

        Submission submission = Submission.builder()
                .problem(problem)
                .user(user)
                .contestId(request.contestId())
                .language(request.language())
                .codeStorageUri(codeStorageUri)
                .status(SubmissionStatus.PENDING)
                .verdict(null)
                .passTestCases(0)
                .totalTestCases(0)
                .retryCount(0)
                .build();

        return submissionRepository.save(submission);
    }

    @Override
    public Submission getSubmissionById(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("Submission not found with id: null");
        }
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
    }

    @Override
    public SubmissionResponse getSubmissionResponseById(Long id) {
        Submission submission = getSubmissionById(id);
        return SubmissionResponse.fromEntity(submission);
    }

    @Override
    public String getSubmissionCode(Long id) {
        Submission submission = getSubmissionById(id);
        if (submission.getCodeStorageUri() == null || submission.getCodeStorageUri().isBlank()) {
            return "";
        }
        String objectName = storageService.extractObjectName(submission.getCodeStorageUri());
        try (java.io.InputStream is = storageService.downloadFile(objectName)) {
            if (is == null) {
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new jsd.toiyeujava.mini_leetcode.exception.StorageException("Failed to read submission code: " + id, e);
        }
    }

    @Override
    public Page<SubmissionResponse> getUserSubmissions(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new ResourceNotFoundException("User not found with id: null");
        }
        return submissionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(SubmissionResponse::fromEntity);
    }

    @Override
    public Page<SubmissionResponse> getProblemSubmissions(Long problemId, Pageable pageable) {
        if (problemId == null) {
            throw new ResourceNotFoundException("Problem not found with id: null");
        }
        return submissionRepository.findByProblemIdOrderByCreatedAtDesc(problemId, pageable)
                .map(SubmissionResponse::fromEntity);
    }

    @Override
    @Transactional
    public Submission updateSubmissionVerdict(
            Long submissionId,
            SubmissionStatus status,
            SubmissionVerdict verdict,
            Integer executionTimeMs,
            Integer memoryUsedKb,
            Integer passTestCases,
            Integer totalTestCases,
            String errorMessage
    ) {
        Submission submission = getSubmissionById(submissionId);
        submission.setStatus(status);
        submission.setVerdict(verdict);
        submission.setExecutionTimeMs(executionTimeMs);
        submission.setMemoryUsedKb(memoryUsedKb);
        submission.setPassTestCases(passTestCases);
        submission.setTotalTestCases(totalTestCases);
        submission.setErrorMessage(errorMessage);
        return submissionRepository.save(submission);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission failSubmission(Long submissionId, String errorMessage) {
        if (submissionId == null) {
            throw new ResourceNotFoundException("Submission not found with id: null");
        }
        String message = (errorMessage != null && !errorMessage.isBlank())
                ? errorMessage
                : "Submission evaluation failed";
        if (message.length() > 2000) {
            message = message.substring(0, 2000) + "...";
        }
        return updateSubmissionVerdict(
                submissionId,
                SubmissionStatus.FAILED,
                null,
                null,
                null,
                0,
                0,
                message
        );
    }
}

