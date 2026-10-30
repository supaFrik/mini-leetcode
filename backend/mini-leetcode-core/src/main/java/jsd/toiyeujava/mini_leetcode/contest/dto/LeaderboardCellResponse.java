package jsd.toiyeujava.mini_leetcode.contest.dto;

public record LeaderboardCellResponse(
        boolean solved,
        int attempts,
        Integer minute
) {
}
