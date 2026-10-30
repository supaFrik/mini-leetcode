package jsd.toiyeujava.mini_leetcode.contest.dto;

import jsd.toiyeujava.mini_leetcode.entity.Difficulty;

public record ContestProblemResponse(
        Long id,
        String slug,
        String title,
        Difficulty difficulty,
        String label
) {
}
