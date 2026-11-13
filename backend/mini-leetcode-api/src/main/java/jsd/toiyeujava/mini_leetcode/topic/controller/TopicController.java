package jsd.toiyeujava.mini_leetcode.topic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.TransitionRequest;
import jsd.toiyeujava.mini_leetcode.topic.dto.TopicDtos.*;
import jsd.toiyeujava.mini_leetcode.topic.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chủ đề học (specs/topics.spec.md). Học: mọi người đã đăng nhập; soạn: INSTRUCTOR / ADMIN. */
@RestController
@RequestMapping("/api/v1/topics")
@Tag(name = "Topics", description = "Theory, quizzes and drag-and-drop exercises attached to project stages")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a topic with my progress (answers are never included)")
    public LearnerTopic get(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        return topicService.get(slug, user.getId(), isStaff(user));
    }

    @PostMapping("/{slug}/steps/{stepId}/attempts")
    @Operation(summary = "Submit an answer for a step (theory: mark as read)")
    public AttemptResult attempt(@PathVariable String slug, @PathVariable Long stepId,
                                 @Valid @RequestBody AttemptRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return topicService.attempt(slug, stepId, request.answer(), user.getId(), isStaff(user));
    }

    @PostMapping("/{slug}/skip")
    @Operation(summary = "Skip a topic")
    public ResponseEntity<Void> skip(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        topicService.skip(slug, user.getId(), isStaff(user), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{slug}/skip")
    @Operation(summary = "Undo skipping a topic")
    public ResponseEntity<Void> unskip(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        topicService.skip(slug, user.getId(), isStaff(user), false);
        return ResponseEntity.noContent().build();
    }

    // ── Soạn ────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "List topics for authoring and for attaching to stages")
    public List<TopicSummary> list(@RequestParam(value = "search", required = false) String search) {
        return topicService.list(search);
    }

    @GetMapping("/{slug}/authoring")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get a topic with its answers for editing")
    public AuthoringTopic getForAuthoring(@PathVariable String slug) {
        return topicService.getForAuthoring(slug);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Create a topic (DRAFT)")
    public ResponseEntity<AuthoringTopic> create(@Valid @RequestBody UpsertTopicRequest request,
                                                 @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(topicService.create(request, user.getId()));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Replace a topic's content (author or admin); steps sent with their id keep learner progress")
    public AuthoringTopic update(@PathVariable String slug, @Valid @RequestBody UpsertTopicRequest request,
                                 @AuthenticationPrincipal UserPrincipal user) {
        return topicService.update(slug, request, user.getId(), user.getRole());
    }

    @PostMapping("/{slug}/transitions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Change status: DRAFT / PUBLISHED / ARCHIVED (author or admin)")
    public AuthoringTopic transition(@PathVariable String slug, @Valid @RequestBody TransitionRequest request,
                                     @AuthenticationPrincipal UserPrincipal user) {
        return topicService.transition(slug, request.status(), user.getId(), user.getRole());
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete a topic that no study plan uses (author or admin)")
    public ResponseEntity<Void> delete(@PathVariable String slug, @AuthenticationPrincipal UserPrincipal user) {
        topicService.delete(slug, user.getId(), user.getRole());
        return ResponseEntity.noContent().build();
    }

    private static boolean isStaff(UserPrincipal user) {
        return user.getRole() != Role.USER;
    }
}
