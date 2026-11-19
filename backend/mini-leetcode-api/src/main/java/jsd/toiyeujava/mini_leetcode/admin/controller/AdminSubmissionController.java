package jsd.toiyeujava.mini_leetcode.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminSubmissionService;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminSubmissionService.SubmissionList;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Mọi bài nộp, cho giảng viên và ADMIN (quy tắc chung /api/v1/admin/** trong SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/submissions")
@Tag(name = "Admin submissions", description = "All submissions with filters (instructor or admin)")
public class AdminSubmissionController {

    private final AdminSubmissionService service;

    public AdminSubmissionController(AdminSubmissionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List submissions (Run excluded), newest first; filter by username, problem title/slug and verdict")
    public SubmissionList list(@RequestParam(required = false) String user,
                               @RequestParam(required = false) String problem,
                               @RequestParam(required = false) String verdict,
                               @RequestParam(defaultValue = "0") int page,
                               @AuthenticationPrincipal UserPrincipal principal) {
        Long owner = principal.getRole() == Role.ADMIN ? null : principal.getId();
        return service.list(user, problem, verdict, page, owner);
    }
}
