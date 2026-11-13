package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemCloneService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Course Builder (specs/course-builder.spec.md): nhân bản stage và mở khoá stage đang chạy. */
@RestController
@RequestMapping("/api/v1/admin/problems/{problemId}")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Course Builder", description = "Clone and unlock problems used as study plan stages")
public class ProblemCloneController {

    public record CloneRequest(@Size(max = 200) String title) {}

    private final ProblemCloneService service;
    private final UserRepository userRepository;

    public ProblemCloneController(ProblemCloneService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @PostMapping("/clone")
    @Operation(summary = "Create a DRAFT copy with grading, starter, reference solution and every test case")
    public ResponseEntity<ProblemResponse> cloneProblem(@PathVariable Long problemId,
                                                        @RequestBody(required = false) CloneRequest request,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.cloneProblem(problemId, request == null ? null : request.title(), creator));
    }

    @PostMapping("/unlock")
    @Operation(summary = "Move a PUBLISHED problem back to DRAFT so its test cases and grading can be edited")
    public ProblemResponse unlock(@PathVariable Long problemId) {
        return service.unlock(problemId);
    }
}
