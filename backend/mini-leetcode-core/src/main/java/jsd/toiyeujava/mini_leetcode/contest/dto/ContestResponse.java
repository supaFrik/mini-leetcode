package jsd.toiyeujava.mini_leetcode.contest.dto;

import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thông tin kỳ thi + trạng thái đăng ký của người dùng hiện tại.
 * Được map sang mảng JSON phẳng cho frontend (services.ts -> ContestInfo).
 */
public record ContestResponse(
        Long id,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ContestStatus status,
        ScoringPolicy scoringPolicy,
        Integer maxViolationsAllowed,
        List<ContestProblemResponse> problems,
        ParticipantStatus myStatus,
        Integer myViolations
) {
}
