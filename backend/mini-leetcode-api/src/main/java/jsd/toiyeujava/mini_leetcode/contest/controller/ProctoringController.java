package jsd.toiyeujava.mini_leetcode.contest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventRequest;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventResponse;
import jsd.toiyeujava.mini_leetcode.contest.service.ProctoringService;
import jsd.toiyeujava.mini_leetcode.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contests")
@RequiredArgsConstructor
@Tag(name = "Contest Proctoring", description = "Server-side integrity evaluation & anti-cheat event processor")
@SecurityRequirement(name = "bearerAuth")
public class ProctoringController {

    private final ProctoringService proctoringService;

    @PostMapping("/{contestId}/proctoring/events")
    @Operation(
            summary = "Report anti-cheat window focus/blur or clipboard paste event",
            description = "Receives window state change event, computes absence duration with 1.5s grace period, and increments violation counter or disqualifies on server side."
    )
    public ResponseEntity<ApiResponse<ProctoringEventResponse>> recordEvent(
            @PathVariable Long contestId,
            @Valid @RequestBody ProctoringEventRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        ProctoringEventResponse response = proctoringService.handleProctoringEvent(
                contestId,
                currentUser.getId(),
                request
        );
        return ResponseEntity.ok(ApiResponse.success("Proctoring event processed", response));
    }
}
