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
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ContestRepository contestRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final SubmissionArchiveService submissionArchiveService;

    @Autowired
    public SubmissionServiceImpl(
            SubmissionRepository submissionRepository,
            ProblemRepository problemRepository,
            UserRepository userRepository,
            StorageService storageService,
            ContestRepository contestRepository,
            ContestParticipantRepository contestParticipantRepository,
            @Autowired(required = false) SubmissionArchiveService submissionArchiveService
    ) {
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.contestRepository = contestRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.submissionArchiveService = submissionArchiveService;
    }

    public SubmissionServiceImpl(
            SubmissionRepository submissionRepository,
            ProblemRepository problemRepository,
            UserRepository userRepository,
            StorageService storageService,
            ContestRepository contestRepository,
            ContestParticipantRepository contestParticipantRepository
    ) {
        this(submissionRepository, problemRepository, userRepository, storageService, contestRepository, contestParticipantRepository, null);
    }

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

        boolean hasFiles = request.files() != null && !request.files().isEmpty();
        boolean hasCode = request.code() != null && !request.code().isBlank();
        boolean hasArchive = request.archiveBytes() != null && request.archiveBytes().length > 0;
        boolean hasStorageUri = request.codeStorageUri() != null && !request.codeStorageUri().isBlank();

        if (!hasFiles && !hasCode && !hasArchive && !hasStorageUri) {
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

            ContestStatus effectiveStatus = contest.resolveStatus();
            if (effectiveStatus != ContestStatus.RUNNING) {
                throw new AccessDeniedException("Contest is not currently active (status: " + effectiveStatus + ")");
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

        String codeStorageUri;
        if (hasStorageUri) {
            codeStorageUri = request.codeStorageUri();
        } else if (hasArchive) {
            if (submissionArchiveService == null) {
                throw new IllegalStateException("SubmissionArchiveService is not configured");
            }
            SubmissionArchiveResult archiveResult = submissionArchiveService.packageAndValidate(
                    null,
                    new ByteArrayInputStream(request.archiveBytes()),
                    null,
                    request.language()
            );
            codeStorageUri = submissionArchiveService.uploadSubmissionArchive(
                    problemId,
                    userId,
                    archiveResult.zipBytes()
            );
        } else if (submissionArchiveService != null && (hasFiles || (hasCode && (request.code().contains("FILE:") || request.code().contains("###"))))) {
            SubmissionArchiveResult archiveResult = submissionArchiveService.packageAndValidate(
                    request.files(),
                    null,
                    request.code(),
                    request.language()
            );
            codeStorageUri = submissionArchiveService.uploadSubmissionArchive(
                    problemId,
                    userId,
                    archiveResult.zipBytes()
            );
        } else {
            String extension = request.language() == SubmissionLanguage.JAVA ? ".java"
                    : (request.language() == SubmissionLanguage.PYTHON ? ".py" : ".cpp");
            String objectName = storageService.generateSubmissionKey(problemId, userId, extension);
            if (objectName == null || objectName.isBlank()) {
                objectName = "submissions/" + problemId + "/" + userId + "/" + java.util.UUID.randomUUID() + extension;
            }

            byte[] codeBytes = (request.code() != null ? request.code() : "").getBytes(StandardCharsets.UTF_8);
            String bucket = storageService.getSubmissionsBucketName();
            if (bucket == null || bucket.isBlank()) {
                bucket = "submissions";
            }
            codeStorageUri = storageService.uploadFile(
                    bucket,
                    objectName,
                    new ByteArrayInputStream(codeBytes),
                    codeBytes.length,
                    "text/plain"
            );
        }

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
        String uri = submission.getCodeStorageUri();
        String objectName = storageService.extractObjectName(uri);
        try (java.io.InputStream is = storageService.downloadFileByUri(uri)) {
            if (is == null) {
                return "";
            }
            if (objectName.endsWith(".zip") || uri.endsWith(".zip")) {
                StringBuilder sb = new StringBuilder();
                try (ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    boolean first = true;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        boolean isSource = name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".cpp")
                                || name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".hpp");
                        if (!entry.isDirectory() && isSource) {
                            if (!first) {
                                sb.append("\n");
                            }
                            sb.append("// === FILE: ").append(name).append(" ===\n");
                            sb.append(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                            if (!sb.toString().endsWith("\n")) {
                                sb.append("\n");
                            }
                            first = false;
                        }
                    }
                }
                return sb.toString();
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

    @Override
    @Transactional(readOnly = true)
    public boolean isTerminal(Long submissionId) {
        if (submissionId == null) {
            return false;
        }
        return submissionRepository.findById(submissionId)
                .map(submission -> submission.getStatus() == SubmissionStatus.COMPLETED
                        || submission.getStatus() == SubmissionStatus.FAILED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBeenJudged(Long submissionId) {
        if (submissionId == null) {
            return false;
        }
        return submissionRepository.findById(submissionId)
                .map(submission -> submission.getStatus() != null
                        && submission.getStatus() != SubmissionStatus.PENDING)
                .orElse(false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetried(Long submissionId) {
        if (submissionId == null) {
            return;
        }
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            return;
        }
        submission.setRetryCount((submission.getRetryCount() == null ? 0 : submission.getRetryCount()) + 1);
        submissionRepository.save(submission);
    }
}
