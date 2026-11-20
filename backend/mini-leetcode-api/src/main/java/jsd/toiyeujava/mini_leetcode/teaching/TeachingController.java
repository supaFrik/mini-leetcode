package jsd.toiyeujava.mini_leetcode.teaching;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/v1/teaching/**: giảng viên và ADMIN (SecurityConfig); số liệu theo nội dung của người gọi. */
@RestController
@RequestMapping("/api/v1/teaching")
@Tag(name = "Teaching", description = "Instructor dashboard and student progress for the caller's own content")
public class TeachingController {

    private final TeachingService service;

    public TeachingController(TeachingService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Learner count, 10 newest submissions to my problems, my upcoming contests")
    public TeachingService.Dashboard dashboard(@AuthenticationPrincipal UserPrincipal user) {
        return service.dashboard(user.getId());
    }

    @GetMapping("/progress")
    @Operation(summary = "Learners x stages for each study plan I created")
    public List<TeachingService.PlanProgress> progress(@AuthenticationPrincipal UserPrincipal user) {
        return service.progress(user.getId());
    }
}
