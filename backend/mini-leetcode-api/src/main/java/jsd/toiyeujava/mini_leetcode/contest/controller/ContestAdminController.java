package jsd.toiyeujava.mini_leetcode.contest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminContestDetail;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminContestSummary;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminParticipant;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.BulkParticipantsRequest;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.BulkParticipantsResult;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.ContestUpsertRequest;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminService;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản lý kỳ thi cho giảng viên/admin. Luật khoá theo trạng thái (đang thi chỉ được gia hạn,
 * đã kết thúc thì chỉ xem) do {@link ContestAdminService} kiểm tra theo giờ server.
 */
@RestController
@RequestMapping("/api/v1/admin/contests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Contest administration", description = "Create, edit, delete contests and manage participants (INSTRUCTOR/ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class ContestAdminController {

    private final ContestAdminService service;

    @GetMapping
    @Operation(summary = "List contests with problem/participant counts, filter by effective status and title")
    public List<AdminContestSummary> list(@RequestParam(required = false) ContestStatus status,
                                          @RequestParam(required = false) String search) {
        return service.list(status, search);
    }

    @GetMapping("/{contestId}")
    @Operation(summary = "Contest details for the editor")
    public AdminContestDetail get(@PathVariable Long contestId) {
        return service.get(contestId);
    }

    @PostMapping
    @Operation(summary = "Create a scheduled contest (start must be in the future, only PUBLISHED problems)")
    public ResponseEntity<AdminContestDetail> create(@RequestBody ContestUpsertRequest request,
                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, currentUser.getId()));
    }

    @PutMapping("/{contestId}")
    @Operation(summary = "Edit a contest",
            description = "SCHEDULED: any field. RUNNING: only a later end time (409 otherwise). COMPLETED: 409. "
                    + "'problems' null keeps the current list; a stale 'version' returns 409.")
    public AdminContestDetail update(@PathVariable Long contestId, @RequestBody ContestUpsertRequest request) {
        return service.update(contestId, request);
    }

    @DeleteMapping("/{contestId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a scheduled contest without submissions (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long contestId) {
        service.delete(contestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{contestId}/participants")
    @Operation(summary = "Participants with status, violations and score")
    public List<AdminParticipant> participants(@PathVariable Long contestId) {
        return service.participants(contestId);
    }

    @PostMapping("/{contestId}/participants")
    @Operation(summary = "Add participants by username/email (one per line or comma-separated, up to 500)")
    public BulkParticipantsResult addParticipants(@PathVariable Long contestId,
                                                  @RequestBody BulkParticipantsRequest request) {
        return service.addParticipants(contestId, request == null ? null : request.entries());
    }

    @DeleteMapping("/{contestId}/participants/{userId}")
    @Operation(summary = "Remove a participant who has no submissions in the contest")
    public ResponseEntity<Void> removeParticipant(@PathVariable Long contestId, @PathVariable Long userId) {
        service.removeParticipant(contestId, userId);
        return ResponseEntity.noContent().build();
    }
}
