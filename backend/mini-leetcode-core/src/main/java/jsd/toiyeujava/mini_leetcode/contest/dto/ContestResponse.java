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
        Integer myViolations,
        /** Hạng của user hiện tại khi kỳ thi đã kết thúc (null nếu không tham gia hoặc bị loại). */
        Integer myRank,
        /** Số bài user hiện tại đã giải trong kỳ thi đã kết thúc (null nếu không tham gia). */
        Integer mySolved
) {
    public ContestResponse(Long id, String title, LocalDateTime startTime, LocalDateTime endTime,
                           ContestStatus status, ScoringPolicy scoringPolicy, Integer maxViolationsAllowed,
                           List<ContestProblemResponse> problems, ParticipantStatus myStatus, Integer myViolations) {
        this(id, title, startTime, endTime, status, scoringPolicy, maxViolationsAllowed, problems,
                myStatus, myViolations, null, null);
    }
}
