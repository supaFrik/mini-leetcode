package jsd.toiyeujava.mini_leetcode.contest.dto;

public record PlagiarismResponse(
        Long id,
        Long problemId,
        String problemTitle,
        String userA,
        String userB,
        double similarity
) {
}
