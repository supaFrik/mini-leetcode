package jsd.toiyeujava.mini_leetcode.contest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.contest.dto.ContestResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.LeaderboardRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.PlagiarismResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctorReportRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API cho kỳ thi / phòng thi.
 * Trước đây chỉ có {@code POST /{contestId}/proctoring/events}, khiến frontend gọi
 * {@code GET /api/v1/contests} bị Spring trả 404 "No static resource".
 */
@RestController
@RequestMapping("/api/v1/contests")
@RequiredArgsConstructor
@Tag(name = "Contests", description = "Contest listing, registration, leaderboard and instructor reports")
@SecurityRequirement(name = "bearerAuth")
public class ContestController {

    private final ContestService contestService;

    @GetMapping
    @Operation(summary = "List contests", description = "Returns all contests with the current user's registration status")
    public ResponseEntity<List<ContestResponse>> getContests(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(contestService.getContests(currentUser.getId()));
    }

    @GetMapping("/{contestId}")
    @Operation(summary = "Get contest details", description = "Returns a contest with its problems and the current user's registration status")
    public ResponseEntity<ContestResponse> getContest(
            @PathVariable Long contestId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(contestService.getContest(contestId, currentUser.getId()));
    }

    @PostMapping("/{contestId}/register")
    @Operation(summary = "Register for a contest", description = "Registers the authenticated user as a participant")
    public ResponseEntity<ContestResponse> register(
            @PathVariable Long contestId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(contestService.register(contestId, currentUser.getId()));
    }

    @GetMapping("/{contestId}/leaderboard")
    @Operation(summary = "Get contest leaderboard", description = "ACM/ICPC ranking: solved desc, then total penalty asc. Disqualified participants are ranked last without a rank.")
    public ResponseEntity<List<LeaderboardRowResponse>> getLeaderboard(@PathVariable Long contestId) {
        return ResponseEntity.ok(contestService.getLeaderboard(contestId));
    }

    @PostMapping("/{contestId}/finalize")
    @Operation(summary = "Finalize contest submission",
            description = "Marks the authenticated participant as SUBMITTED, locking further submissions. Allowed after the contest ends so the client can auto-finalize when time runs out.")
    public ResponseEntity<ContestResponse> finalizeSubmission(
            @PathVariable Long contestId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(contestService.finalizeSubmission(contestId, currentUser.getId()));
    }

    @GetMapping("/{contestId}/proctoring/report")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get proctoring report", description = "Instructor/Admin view of violations per participant")
    public ResponseEntity<List<ProctorReportRowResponse>> getProctoringReport(@PathVariable Long contestId) {
        return ResponseEntity.ok(contestService.getProctoringReport(contestId));
    }

    @PostMapping("/{contestId}/participants/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Unlock a disqualified participant", description = "Admin only. Resets the participant's violation count.")
    public ResponseEntity<Void> unlockParticipant(
            @PathVariable Long contestId,
            @PathVariable Long userId
    ) {
        contestService.unlockParticipant(contestId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{contestId}/plagiarism")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get plagiarism report", description = "Similarity pairs between participants' accepted solutions")
    public ResponseEntity<List<PlagiarismResponse>> getPlagiarism(@PathVariable Long contestId) {
        return ResponseEntity.ok(contestService.getPlagiarism(contestId));
    }
}
