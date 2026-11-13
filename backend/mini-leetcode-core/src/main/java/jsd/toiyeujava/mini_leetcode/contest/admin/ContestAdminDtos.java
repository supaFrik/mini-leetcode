package jsd.toiyeujava.mini_leetcode.contest.admin;

import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.RegistrationMode;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của API quản lý kỳ thi ({@code /api/v1/admin/contests}). Mốc thời gian vào/ra đều là UTC. */
public final class ContestAdminDtos {

    private ContestAdminDtos() {
    }

    /** Một bài trong kỳ thi. {@code label} trống → tự gán A, B, C… theo thứ tự. */
    public record ContestProblemInput(Long problemId, String label, Integer orderIndex) {
    }

    /**
     * Tạo hoặc sửa kỳ thi. Khi sửa: {@code problems = null} nghĩa là giữ nguyên danh sách bài;
     * {@code version} là phiên bản client đã đọc, lệch thì trả 409.
     */
    public record ContestUpsertRequest(
            String title,
            Instant startTime,
            Instant endTime,
            ScoringPolicy scoringPolicy,
            Integer maxViolationsAllowed,
            RegistrationMode registrationMode,
            List<ContestProblemInput> problems,
            Long version) {
    }

    public record AdminContestSummary(
            Long id,
            String title,
            String slug,
            ContestStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            RegistrationMode registrationMode,
            long problemCount,
            long participantCount) {
    }

    public record AdminContestProblem(
            Long problemId,
            String slug,
            String title,
            Difficulty difficulty,
            ProblemStatus problemStatus,
            String label,
            int orderIndex) {
    }

    public record AdminContestDetail(
            Long id,
            String title,
            String slug,
            ContestStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            ScoringPolicy scoringPolicy,
            Integer maxViolationsAllowed,
            RegistrationMode registrationMode,
            String createdBy,
            Long version,
            boolean hasSubmissions,
            List<AdminContestProblem> problems) {
    }

    public record AdminParticipant(
            Long userId,
            String username,
            String email,
            ParticipantStatus status,
            int violationCount,
            int solved,
            int penaltyMinutes,
            Integer rank,
            boolean hasSubmissions) {
    }

    /** Danh sách username/email, mỗi dòng một người hoặc ngăn bởi dấu phẩy / chấm phẩy. */
    public record BulkParticipantsRequest(String entries) {
    }

    public enum BulkOutcome {
        ADDED,
        ALREADY_REGISTERED,
        NOT_FOUND,
        BLOCKED
    }

    public record BulkParticipantLine(String input, BulkOutcome outcome, String username) {
    }

    public record BulkParticipantsResult(List<BulkParticipantLine> lines, int added) {
    }
}
