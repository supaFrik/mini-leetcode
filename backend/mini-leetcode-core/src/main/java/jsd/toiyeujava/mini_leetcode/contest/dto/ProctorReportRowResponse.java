package jsd.toiyeujava.mini_leetcode.contest.dto;

import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;

import java.time.LocalDateTime;

public record ProctorReportRowResponse(
        Long userId,
        String username,
        ParticipantStatus status,
        Integer violationCount,
        LocalDateTime lastEventAt
) {
}
