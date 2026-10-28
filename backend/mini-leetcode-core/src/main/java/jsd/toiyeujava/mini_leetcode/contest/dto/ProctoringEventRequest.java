package jsd.toiyeujava.mini_leetcode.contest.dto;

import jakarta.validation.constraints.NotNull;
import jsd.toiyeujava.mini_leetcode.entity.ProctoringEventType;

public record ProctoringEventRequest(
        @NotNull(message = "Event type is required (WINDOW_BLUR, WINDOW_FOCUS, or CLIPBOARD_PASTE)")
        ProctoringEventType eventType,

        Long clientTimestamp
) {
}
