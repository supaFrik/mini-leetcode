package jsd.toiyeujava.mini_leetcode.submission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.dto.ApiResponse;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.service.SourcePathValidator;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionArchiveService;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Submissions", description = "Endpoints for code submission and result inquiry")
@SecurityRequirement(name = "bearerAuth")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionProducer submissionProducer;
    private final SubmissionArchiveService submissionArchiveService;

    @Autowired
    public SubmissionController(
            SubmissionService submissionService,
            SubmissionProducer submissionProducer,
            @Autowired(required = false) SubmissionArchiveService submissionArchiveService
    ) {
        this.submissionService = submissionService;
        this.submissionProducer = submissionProducer;
        this.submissionArchiveService = submissionArchiveService;
    }

    public SubmissionController(SubmissionService submissionService, SubmissionProducer submissionProducer) {
        this(submissionService, submissionProducer, null);
    }

    // =========================================================================
    // Milestone 2 Unified Ingestion API: POST /api/v1/submissions (JSON)
    // =========================================================================
    @PostMapping(value = "/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit multi-file code via JSON", description = "Validates files, normalizes to ZIP, uploads to MinIO submissions bucket, and enqueues to Redis")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitCodeJson(
            @Valid @RequestBody SubmitCodeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        if (request.problemId() == null) {
            throw new IllegalArgumentException("Problem ID is required");
        }
        if (request.language() == null) {
            throw new IllegalArgumentException("Language is required");
        }
        boolean hasFiles = request.files() != null && !request.files().isEmpty();
        boolean hasCode = request.code() != null && !request.code().isBlank();
        if (!hasFiles && !hasCode) {
            throw new IllegalArgumentException("Submission must contain files or code");
        }

        if (request.files() != null) {
            SourcePathValidator.validateEntryCount(request.files().size());
            long totalSize = 0;
            java.util.Set<String> seenPaths = new java.util.HashSet<>();
            for (SubmissionFileDto fileDto : request.files()) {
                if (fileDto != null) {
                    validateFilePath(fileDto.path());
                    if (fileDto.path() != null && !seenPaths.add(fileDto.path())) {
                        throw new IllegalArgumentException("Duplicate file path detected: " + fileDto.path());
                    }
                    long fileSize = fileDto.content() != null ? fileDto.content().getBytes(StandardCharsets.UTF_8).length : 0;
                    SourcePathValidator.validateSingleFileSize(fileDto.path(), fileSize);
                    totalSize += fileSize;
                    SourcePathValidator.validateUncompressedSize(totalSize);
                }
            }
        }

        // Persist Submission record and upload archive via Service
        Submission submission = submissionService.createSubmission(
                request.problemId(),
                currentUser.getId(),
                request
        );

        // 4. Dispatch task to Redis Stream (stream:submissions)
        boolean isQuickRun = Boolean.TRUE.equals(request.isQuickRun());
        SubmissionTaskMessage taskMessage = new SubmissionTaskMessage(
                submission.getId(),
                request.problemId(),
                currentUser.getId(),
                request.language(),
                isQuickRun
        );
        dispatchTask(submission.getId(), taskMessage);

        SubmissionResponse response = SubmissionResponse.fromEntity(submission);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Submission accepted and queued for judging", response));
    }

    // =========================================================================
    // Milestone 2 Unified Ingestion API: POST /api/v1/submissions (Multipart)
    // =========================================================================
    @PostMapping(value = {"/submissions", "/problems/{problemId}/submit-multipart", "/problems/{problemId}/run-multipart"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit code via Multipart ZIP or Java files", description = "Handles ZIP archive or multiple .java files")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitCodeMultipart(
            @PathVariable(value = "problemId", required = false) Long pathProblemId,
            @RequestParam(value = "problemId", required = false) Long paramProblemId,
            @RequestParam("language") SubmissionLanguage language,
            @RequestParam(value = "contestId", required = false) Long contestId,
            @RequestParam(value = "isQuickRun", required = false, defaultValue = "false") Boolean isQuickRun,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) throws IOException {
        Long problemId = pathProblemId != null ? pathProblemId : paramProblemId;
        if (httpRequest != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().endsWith("/run-multipart")) {
            isQuickRun = true;
        }
        if (problemId == null) {
            throw new IllegalArgumentException("Problem ID is required");
        }
        if (language == null) {
            throw new IllegalArgumentException("Language is required");
        }

        SubmitCodeRequest request;

        if (file != null && !file.isEmpty()) {
            if (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
                validateFilePath(file.getOriginalFilename());
            }
            int entryCount = 0;
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
                java.util.zip.ZipEntry entry;
                long totalUncompressedSize = 0;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    if (SourcePathValidator.isIgnoredMetadata(entry.getName())) {
                        continue;
                    }
                    entryCount++;
                    SourcePathValidator.validateEntryCount(entryCount);
                    validateFilePath(entry.getName());

                    long entrySize = 0;
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        entrySize += read;
                        totalUncompressedSize += read;
                        SourcePathValidator.validateSingleFileSize(entry.getName(), entrySize);
                        SourcePathValidator.validateUncompressedSize(totalUncompressedSize);
                    }
                }
            } catch (InvalidSubmissionPathException | PayloadTooLargeException e) {
                throw e;
            } catch (Exception ignored) {
                // If not a valid zip stream, packageAndValidate handles or ignores
            }

            if (entryCount == 0) {
                throw new IllegalArgumentException("ZIP archive contains no valid files");
            }

            request = new SubmitCodeRequest(
                    problemId,
                    language,
                    null,
                    List.of(),
                    contestId,
                    isQuickRun,
                    file.getBytes()
            );
        } else if (files != null && !files.isEmpty()) {
            SourcePathValidator.validateEntryCount(files.size());
            long totalSize = 0;
            java.util.Set<String> seenFilenames = new java.util.HashSet<>();
            List<SubmissionFileDto> fileDtos = new ArrayList<>();
            for (MultipartFile mf : files) {
                if (mf != null && !mf.isEmpty()) {
                    String filename = mf.getOriginalFilename();
                    validateFilePath(filename);
                    if (filename != null && !seenFilenames.add(filename)) {
                        throw new IllegalArgumentException("Duplicate filename detected: " + filename);
                    }
                    SourcePathValidator.validateSingleFileSize(filename, mf.getSize());
                    totalSize += mf.getSize();
                    SourcePathValidator.validateUncompressedSize(totalSize);
                    fileDtos.add(new SubmissionFileDto(
                            filename,
                            new String(mf.getBytes(), StandardCharsets.UTF_8)
                    ));
                }
            }
            if (fileDtos.isEmpty()) {
                throw new IllegalArgumentException("No valid files provided in submission");
            }
            request = new SubmitCodeRequest(
                    problemId,
                    language,
                    null,
                    fileDtos,
                    contestId,
                    isQuickRun
            );
        } else {
            throw new IllegalArgumentException("No submission files provided");
        }

        Submission submission = submissionService.createSubmission(
                problemId,
                currentUser.getId(),
                request
        );

        SubmissionTaskMessage taskMessage = new SubmissionTaskMessage(
                submission.getId(),
                problemId,
                currentUser.getId(),
                language,
                Boolean.TRUE.equals(isQuickRun)
        );
        dispatchTask(submission.getId(), taskMessage);

        SubmissionResponse response = SubmissionResponse.fromEntity(submission);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Submission accepted and queued for judging", response));
    }

    // =========================================================================
    // Backwards Compatibility: POST /problems/{id}/submit & /problems/{id}/run
    // =========================================================================
    @PostMapping({"/problems/{problemId}/submit", "/problems/{problemId}/run"})
    @Operation(summary = "Submit or run code for a problem", description = "Accepts code, persists as PENDING, enqueues to Redis queue, and returns 202 ACCEPTED")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitCode(
            @PathVariable Long problemId,
            @Valid @RequestBody SubmitCodeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest
    ) {
        if (request.files() != null) {
            SourcePathValidator.validateEntryCount(request.files().size());
            long totalSize = 0;
            java.util.Set<String> seenPaths = new java.util.HashSet<>();
            for (SubmissionFileDto fileDto : request.files()) {
                if (fileDto != null) {
                    validateFilePath(fileDto.path());
                    if (fileDto.path() != null && !seenPaths.add(fileDto.path())) {
                        throw new IllegalArgumentException("Duplicate file path detected: " + fileDto.path());
                    }
                    long fileSize = fileDto.content() != null ? fileDto.content().getBytes(StandardCharsets.UTF_8).length : 0;
                    SourcePathValidator.validateSingleFileSize(fileDto.path(), fileSize);
                    totalSize += fileSize;
                    SourcePathValidator.validateUncompressedSize(totalSize);
                }
            }
        }

        if (submissionArchiveService != null && request.code() != null && !request.code().isBlank()) {
            submissionArchiveService.packageAndValidate(null, null, request.code(), request.language());
        }

        boolean isRunEndpoint = httpRequest != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().endsWith("/run");
        boolean isQuickRun = isRunEndpoint || Boolean.TRUE.equals(request.isQuickRun());
        // /run không gửi cờ trong body; ghi cờ vào request trước khi lưu để DB khớp với task.
        SubmitCodeRequest toSave = isQuickRun == Boolean.TRUE.equals(request.isQuickRun()) ? request
                : new SubmitCodeRequest(request.problemId(), request.language(), request.code(), request.files(),
                        request.contestId(), true, request.archiveBytes(), request.codeStorageUri());

        Submission submission = submissionService.createSubmission(problemId, currentUser.getId(), toSave);

        SubmissionTaskMessage taskMessage = new SubmissionTaskMessage(
                submission.getId(),
                problemId,
                currentUser.getId(),
                request.language(),
                isQuickRun
        );

        dispatchTask(submission.getId(), taskMessage);

        SubmissionResponse response = SubmissionResponse.fromEntity(submission);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Submission accepted and queued for judging", response));
    }

    private void dispatchTask(Long submissionId, SubmissionTaskMessage taskMessage) {
        try {
            submissionProducer.enqueueSubmission(taskMessage);
        } catch (Exception e) {
            log.error("Failed to enqueue submission {} into Redis: {}", submissionId, e.getMessage(), e);
            String rawReason = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            String reason = rawReason.length() > 500 ? rawReason.substring(0, 500) + "..." : rawReason;
            try {
                submissionService.failSubmission(
                        submissionId,
                        "Failed to enqueue submission task for evaluation: " + reason
                );
            } catch (Exception dbEx) {
                log.error("Failed to mark submission {} as FAILED after Redis enqueue failure: {}", submissionId, dbEx.getMessage(), dbEx);
                e.addSuppressed(dbEx);
            }
            throw new RuntimeException("Failed to enqueue submission task for evaluation: " + reason, e);
        }
    }

    @GetMapping("/submissions/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Operation(summary = "Get submission details", description = "Returns submission status, verdict, runtime, and memory metrics")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getSubmission(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Submission submission = submissionService.getSubmissionById(id);
        checkSubmissionAccess(submission, currentUser, "You do not have permission to view this submission");
        return ResponseEntity.ok(ApiResponse.success(SubmissionResponse.fromEntity(submission)));
    }

    @GetMapping("/submissions/{id}/code")
    @Operation(summary = "Get submitted source code", description = "Fetches the full raw source code of the submission from storage")
    public ResponseEntity<ApiResponse<String>> getSubmissionCode(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Submission submission = submissionService.getSubmissionById(id);
        checkSubmissionAccess(submission, currentUser, "You do not have permission to view this submission code");
        String code = submissionService.getSubmissionCode(id);
        return ResponseEntity.ok(ApiResponse.success("Source code retrieved successfully", code));
    }

    private void checkSubmissionAccess(Submission submission, UserPrincipal currentUser, String errorMessage) {
        if (submission == null) {
            throw new ResourceNotFoundException("Submission not found with id: null");
        }
        if (currentUser == null) {
            throw new AccessDeniedException("Unauthenticated user cannot access submission");
        }
        boolean isOwner = submission.getUser() != null && submission.getUser().getId().equals(currentUser.getId());
        boolean isStaff = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.INSTRUCTOR;
        if (!isOwner && !isStaff) {
            throw new AccessDeniedException(errorMessage);
        }
    }

    private void validateFilePath(String path) {
        if (path == null) {
            return;
        }
        for (char c : path.toCharArray()) {
            if (c == ';' || c == '&' || c == '|' || c == '<' || c == '>' || c == '`') {
                throw new InvalidSubmissionPathException("Dangerous shell character '" + c + "' in path: " + path);
            }
        }
        SourcePathValidator.sanitizeAndValidatePath(path);
    }

    @GetMapping("/submissions/my")
    @Operation(summary = "Get my submissions", description = "Returns a paginated list of submissions for the current authenticated user, newest first, optionally filtered by verdict and language")
    public ResponseEntity<ApiResponse<Page<SubmissionResponse>>> getMySubmissions(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Verdict name (AC, WA, TLE, MLE, RE, CE) or PENDING; a FAILED submission without verdict counts as RE")
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) SubmissionLanguage language,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // Không lọc thì giữ nguyên đường cũ cho các chỗ đang gọi.
        Page<SubmissionResponse> responses = (verdict == null || verdict.isBlank()) && language == null
                ? submissionService.getUserSubmissions(currentUser.getId(), pageable)
                : submissionService.getUserSubmissions(currentUser.getId(), verdict, language, pageable);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/submissions/my/summary")
    @Operation(summary = "Summarise my submissions", description = "Counts all of the current user's submissions per verdict (plus PENDING)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getMySubmissionSummary(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.getUserSubmissionSummary(currentUser.getId())));
    }

    @GetMapping("/problems/{problemId}/submissions")
    @Operation(summary = "Get submissions for a problem", description = "Returns a paginated list of submissions for a specific problem")
    public ResponseEntity<ApiResponse<Page<SubmissionResponse>>> getProblemSubmissions(
            @PathVariable Long problemId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SubmissionResponse> responses = submissionService.getProblemSubmissions(problemId, pageable);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
