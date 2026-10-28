package jsd.toiyeujava.mini_leetcode.submission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.dto.ApiResponse;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Submissions", description = "Endpoints for code submission and result inquiry")
@SecurityRequirement(name = "bearerAuth")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionProducer submissionProducer;

    @PostMapping("/problems/{problemId}/submit")
    @Operation(summary = "Submit code for a problem", description = "Accepts code, persists as PENDING, enqueues to Redis queue, and returns 202 ACCEPTED")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitCode(
            @PathVariable Long problemId,
            @Valid @RequestBody SubmitCodeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Submission submission = submissionService.createSubmission(problemId, currentUser.getId(), request);

        SubmissionTaskMessage taskMessage = new SubmissionTaskMessage(
                submission.getId(),
                problemId,
                currentUser.getId(),
                request.language()
        );

        try {
            submissionProducer.enqueueSubmission(taskMessage);
        } catch (Exception e) {
            log.error("Failed to enqueue submission {} into Redis: {}", submission.getId(), e.getMessage(), e);
            String rawReason = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            String reason = rawReason.length() > 500 ? rawReason.substring(0, 500) + "..." : rawReason;
            try {
                submissionService.failSubmission(
                        submission.getId(),
                        "Failed to enqueue submission task for evaluation: " + reason
                );
            } catch (Exception dbEx) {
                log.error("Failed to mark submission {} as FAILED after Redis enqueue failure: {}", submission.getId(), dbEx.getMessage(), dbEx);
                e.addSuppressed(dbEx);
            }
            throw new RuntimeException("Failed to enqueue submission task for evaluation: " + reason, e);
        }

        SubmissionResponse response = SubmissionResponse.fromEntity(submission);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Submission accepted and queued for judging", response));
    }

    @GetMapping("/submissions/{id}")
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

    @GetMapping("/submissions/my")
    @Operation(summary = "Get my submissions", description = "Returns a paginated list of submissions for the current authenticated user")
    public ResponseEntity<ApiResponse<Page<SubmissionResponse>>> getMySubmissions(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SubmissionResponse> responses = submissionService.getUserSubmissions(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(responses));
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
