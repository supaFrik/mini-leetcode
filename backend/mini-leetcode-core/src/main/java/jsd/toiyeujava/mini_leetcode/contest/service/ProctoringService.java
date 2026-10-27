package jsd.toiyeujava.mini_leetcode.contest.service;

import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventRequest;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventResponse;

public interface ProctoringService {

    ProctoringEventResponse handleProctoringEvent(Long contestId, Long userId, ProctoringEventRequest request);
}
