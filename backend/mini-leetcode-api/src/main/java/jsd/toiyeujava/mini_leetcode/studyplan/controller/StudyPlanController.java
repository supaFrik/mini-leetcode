package jsd.toiyeujava.mini_leetcode.studyplan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.PlanDetail;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.PlanSummary;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.TransitionRequest;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.UpsertPlanRequest;
import jsd.toiyeujava.mini_leetcode.studyplan.service.StudyPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lộ trình học (specs/study-plan.spec.md). Đọc: mọi người đã đăng nhập; ghi: INSTRUCTOR / ADMIN. */
@RestController
@RequestMapping("/api/v1/study-plans")
@Tag(name = "Study Plans", description = "Project-based learning paths")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @GetMapping
    @Operation(summary = "List study plans with my progress")
    public List<PlanSummary> list(@AuthenticationPrincipal UserPrincipal user) {
        return studyPlanService.list(user.getId(), isStaff(user));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a study plan with stage status for me")
    public PlanDetail get(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        return studyPlanService.get(slug, user.getId(), isStaff(user));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Create a study plan (DRAFT)")
    public ResponseEntity<PlanDetail> create(@Valid @RequestBody UpsertPlanRequest request,
                                             @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studyPlanService.create(request, user.getId()));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Replace a study plan's content (author or admin)")
    public PlanDetail update(@PathVariable String slug, @Valid @RequestBody UpsertPlanRequest request,
                             @AuthenticationPrincipal UserPrincipal user) {
        return studyPlanService.update(slug, request, user.getId(), user.getRole());
    }

    @PostMapping("/{slug}/transitions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Change status: DRAFT / PUBLISHED / ARCHIVED (author or admin)")
    public PlanDetail transition(@PathVariable String slug, @Valid @RequestBody TransitionRequest request,
                                 @AuthenticationPrincipal UserPrincipal user) {
        return studyPlanService.transition(slug, request.status(), user.getId(), user.getRole());
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete a study plan (author or admin)")
    public ResponseEntity<Void> delete(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        studyPlanService.delete(slug, user.getId(), user.getRole());
        return ResponseEntity.noContent().build();
    }

    private static boolean isStaff(UserPrincipal user) {
        return user.getRole() != Role.USER;
    }
}
