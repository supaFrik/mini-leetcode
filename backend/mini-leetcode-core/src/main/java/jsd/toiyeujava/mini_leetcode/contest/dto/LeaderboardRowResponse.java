package jsd.toiyeujava.mini_leetcode.contest.dto;

import java.util.Map;

public record LeaderboardRowResponse(
        Integer rank,
        Long userId,
        String username,
        int solved,
        int penaltyMinutes,
        boolean disqualified,
        Map<Long, LeaderboardCellResponse> cells
) {
}
