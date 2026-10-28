package jsd.toiyeujava.mini_leetcode.contest.service;

import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventRequest;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProctoringEventType;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProctoringServiceImpl implements ProctoringService {

    private static final long GRACE_PERIOD_MS = 1500L; // 1.5 seconds

    private final ContestParticipantRepository participantRepository;

    @Override
    @Transactional
    public ProctoringEventResponse handleProctoringEvent(Long contestId, Long userId, ProctoringEventRequest request) {
        if (contestId == null) {
            throw new ResourceNotFoundException("Contest not found with id: null");
        }
        if (userId == null) {
            throw new ResourceNotFoundException("User not found with id: null");
        }

        ContestParticipant participant = participantRepository.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Participant not registered in contest id: " + contestId + " with user id: " + userId
                ));

        Contest contest = participant.getContest();
        int maxViolations = (contest != null && contest.getMaxViolationsAllowed() != null)
                ? contest.getMaxViolationsAllowed()
                : 3;
        int currentViolations = participant.getViolationCount() != null ? participant.getViolationCount() : 0;

        if (participant.getStatus() == ParticipantStatus.DISQUALIFIED) {
            return new ProctoringEventResponse(
                    contestId,
                    userId,
                    ParticipantStatus.DISQUALIFIED,
                    currentViolations,
                    maxViolations,
                    true,
                    null,
                    "Participant has already been disqualified"
            );
        }

        if (participant.getStatus() == ParticipantStatus.SUBMITTED) {
            return new ProctoringEventResponse(
                    contestId,
                    userId,
                    ParticipantStatus.SUBMITTED,
                    currentViolations,
                    maxViolations,
                    false,
                    null,
                    "Participant has already finalized their contest submission"
            );
        }

        if (contest != null && contest.getStatus() != ContestStatus.RUNNING) {
            throw new AccessDeniedException("Contest is not currently active (status: " + contest.getStatus() + ")");
        }

        if (request == null || request.eventType() == null) {
            throw new IllegalArgumentException("Event type is required (WINDOW_BLUR, WINDOW_FOCUS, or CLIPBOARD_PASTE)");
        }

        LocalDateTime now = LocalDateTime.now();

        if (request.eventType() == ProctoringEventType.CLIPBOARD_PASTE) {
            int newViolations = currentViolations + 1;
            participant.setViolationCount(newViolations);

            boolean disqualified = newViolations >= maxViolations;
            if (disqualified) {
                participant.setStatus(ParticipantStatus.DISQUALIFIED);
                log.warn("Participant user={} disqualified due to unauthorized clipboard paste in contest={} (violations: {}/{})",
                        userId, contestId, newViolations, maxViolations);
            }
            participantRepository.save(participant);

            String msg = disqualified
                    ? "Disqualified: unauthorized clipboard paste detected (" + newViolations + "/" + maxViolations + ")"
                    : "Violation recorded: unauthorized clipboard paste detected (" + newViolations + "/" + maxViolations + ")";

            return new ProctoringEventResponse(
                    contestId,
                    userId,
                    participant.getStatus(),
                    newViolations,
                    maxViolations,
                    disqualified,
                    null,
                    msg
            );
        }

        if (request.eventType() == ProctoringEventType.WINDOW_BLUR) {
            if (participant.getLastAwayStartTime() == null) {
                participant.setLastAwayStartTime(now);
                participantRepository.save(participant);
            }
            return new ProctoringEventResponse(
                    contestId,
                    userId,
                    participant.getStatus(),
                    currentViolations,
                    maxViolations,
                    false,
                    null,
                    "Blur event recorded. Timer started."
            );
        }

        if (request.eventType() == ProctoringEventType.WINDOW_FOCUS) {
            if (participant.getLastAwayStartTime() == null) {
                return new ProctoringEventResponse(
                        contestId,
                        userId,
                        participant.getStatus(),
                        currentViolations,
                        maxViolations,
                        false,
                        0L,
                        "Focus event ignored: no corresponding blur event active"
                );
            }

            long durationMs = Math.max(0L, Duration.between(participant.getLastAwayStartTime(), now).toMillis());
            participant.setLastAwayStartTime(null);

            if (durationMs < GRACE_PERIOD_MS) {
                participantRepository.save(participant);
                return new ProctoringEventResponse(
                        contestId,
                        userId,
                        participant.getStatus(),
                        currentViolations,
                        maxViolations,
                        false,
                        durationMs,
                        "Focus regained within grace period (" + durationMs + "ms < " + GRACE_PERIOD_MS + "ms). No penalty."
                );
            }

            // Away duration >= grace period -> penalty
            int newViolations = currentViolations + 1;
            participant.setViolationCount(newViolations);

            boolean disqualified = newViolations >= maxViolations;
            if (disqualified) {
                participant.setStatus(ParticipantStatus.DISQUALIFIED);
                log.warn("Participant user={} disqualified in contest={} (violations: {}/{})",
                        userId, contestId, newViolations, maxViolations);
            }

            participantRepository.save(participant);

            String msg = disqualified
                    ? "Disqualified: exceeded maximum permitted window switches (" + newViolations + "/" + maxViolations + ")"
                    : "Violation recorded: away for " + durationMs + "ms (" + newViolations + "/" + maxViolations + ")";

            return new ProctoringEventResponse(
                    contestId,
                    userId,
                    participant.getStatus(),
                    newViolations,
                    maxViolations,
                    disqualified,
                    durationMs,
                    msg
            );
        }

        throw new IllegalArgumentException("Unsupported event type: " + request.eventType());
    }
}
