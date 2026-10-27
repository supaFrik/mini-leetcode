package jsd.toiyeujava.mini_leetcode.contest.dto;

import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;

public record ProctoringEventResponse(
        Long contestId,
        Long userId,
        ParticipantStatus status,
        int violationCount,
        int maxViolationsAllowed,
        boolean isDisqualified,
        Long awayDurationMs,
        String message
) {
}
