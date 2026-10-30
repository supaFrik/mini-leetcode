package jsd.toiyeujava.mini_leetcode.contest.service;

import jsd.toiyeujava.mini_leetcode.contest.dto.ContestProblemResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.ContestResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.LeaderboardCellResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.LeaderboardRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.PlagiarismResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctorReportRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import jsd.toiyeujava.mini_leetcode.contest.entity.ProctoringEvent;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ProctoringEventRepository;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestService {

    /** Luật ACM/ICPC: mỗi lần sai trước khi AC bị phạt 20 phút. */
    private static final int WRONG_ATTEMPT_PENALTY_MINUTES = 20;

    private final ContestRepository contestRepository;
    private final ContestParticipantRepository participantRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    // ---------------- Contests ----------------

    @Transactional(readOnly = true)
    public List<ContestResponse> getContests(Long userId) {
        return contestRepository.findAll().stream()
                .map(c -> toResponse(c, userId))
                .sorted(Comparator
                        .comparing((ContestResponse c) -> c.status() == ContestStatus.RUNNING ? 0 : c.status() == ContestStatus.SCHEDULED ? 1 : 2)
                        .thenComparing(ContestResponse::startTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContestResponse getContest(Long contestId, Long userId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));
        return toResponse(contest, userId);
    }

    @Transactional
    public ContestResponse register(Long contestId, Long userId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        ContestStatus effective = contest.resolveStatus();
        if (effective == ContestStatus.COMPLETED) {
            throw new AccessDeniedException("Contest has already ended");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        participantRepository.findByContestIdAndUserId(contestId, userId)
                .orElseGet(() -> participantRepository.save(ContestParticipant.builder()
                        .contest(contest)
                        .user(user)
                        .status(ParticipantStatus.REGISTERED)
                        .violationCount(0)
                        .score(0)
                        .penaltyTimeMinutes(0)
                        .build()));

        return toResponse(contest, userId);
    }

    private ContestResponse toResponse(Contest contest, Long userId) {
        List<ContestProblemResponse> problems = contestProblemRepository
                .findByContestIdOrderByOrderIndexAsc(contest.getId()).stream()
                .map(cp -> new ContestProblemResponse(
                        cp.getProblem().getId(),
                        cp.getProblem().getSlug(),
                        cp.getProblem().getTitle(),
                        cp.getProblem().getDifficulty(),
                        cp.getLabel()))
                .collect(Collectors.toList());

        ParticipantStatus myStatus = null;
        Integer myViolations = null;
        if (userId != null) {
            ContestParticipant participant = participantRepository
                    .findByContestIdAndUserId(contest.getId(), userId).orElse(null);
            if (participant != null) {
                myStatus = participant.getStatus();
                myViolations = participant.getViolationCount();
            }
        }

        return new ContestResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getStartTime(),
                contest.getEndTime(),
                contest.resolveStatus(),
                contest.getScoringPolicy(),
                contest.getMaxViolationsAllowed(),
                problems,
                myStatus,
                myViolations);
    }

    // ---------------- Leaderboard ----------------

    @Transactional(readOnly = true)
    public List<LeaderboardRowResponse> getLeaderboard(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        List<ContestProblem> contestProblems = contestProblemRepository
                .findByContestIdOrderByOrderIndexAsc(contestId);
        List<Long> problemIds = contestProblems.stream().map(cp -> cp.getProblem().getId()).toList();

        List<Submission> submissions = submissionRepository.findByContestId(contestId);
        List<ContestParticipant> participants = participantRepository.findByContestId(contestId);

        // Gom bài nộp theo (userId, problemId), sắp xếp theo thời gian tăng dần.
        Map<Long, Map<Long, List<Submission>>> byUser = new HashMap<>();
        for (Submission s : submissions) {
            if (s.getUser() == null || s.getProblem() == null) {
                continue;
            }
            byUser.computeIfAbsent(s.getUser().getId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getProblem().getId(), k -> new ArrayList<>())
                    .add(s);
        }
        byUser.values().forEach(m -> m.values().forEach(l -> l.sort(Comparator.comparing(
                Submission::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))));

        LocalDateTime start = contest.getStartTime();

        List<LeaderboardRowResponse> rows = new ArrayList<>();
        Set<Long> seenUsers = new HashSet<>();

        for (ContestParticipant participant : participants) {
            if (participant.getUser() == null) {
                continue;
            }
            Long uid = participant.getUser().getId();
            seenUsers.add(uid);
            rows.add(buildRow(uid, participant.getUser().getUsername(), byUser, problemIds, start,
                    participant.getStatus() == ParticipantStatus.DISQUALIFIED));
        }

        // Thí sinh có bài nộp nhưng chưa đăng ký (dữ liệu cũ) -> vẫn hiện trên bảng.
        for (Map.Entry<Long, Map<Long, List<Submission>>> entry : byUser.entrySet()) {
            if (seenUsers.contains(entry.getKey())) {
                continue;
            }
            String username = entry.getValue().values().stream()
                    .flatMap(List::stream).findFirst()
                    .map(s -> s.getUser().getUsername())
                    .orElse("user-" + entry.getKey());
            Map<Long, Map<Long, List<Submission>>> wrapper = new HashMap<>();
            wrapper.put(entry.getKey(), entry.getValue());
            rows.add(buildRow(entry.getKey(), username, wrapper, problemIds, start, false));
        }

        // Xếp hạng: số bài giải giảm dần, rồi tổng penalty tăng dần.
        // Thí sinh bị loại (DISQUALIFIED) đẩy xuống cuối và không có rank.
        List<LeaderboardRowResponse> ranked = rows.stream()
                .filter(r -> !r.disqualified())
                .sorted(Comparator.comparingInt(LeaderboardRowResponse::solved).reversed()
                        .thenComparingInt(LeaderboardRowResponse::penaltyMinutes)
                        .thenComparing(LeaderboardRowResponse::username))
                .collect(Collectors.toList());

        List<LeaderboardRowResponse> disqualified = rows.stream()
                .filter(LeaderboardRowResponse::disqualified)
                .sorted(Comparator.comparingInt(LeaderboardRowResponse::solved).reversed()
                        .thenComparingInt(LeaderboardRowResponse::penaltyMinutes)
                        .thenComparing(LeaderboardRowResponse::username))
                .collect(Collectors.toList());

        List<LeaderboardRowResponse> result = new ArrayList<>();
        int rank = 1;
        for (LeaderboardRowResponse r : ranked) {
            result.add(new LeaderboardRowResponse(rank++, r.userId(), r.username(), r.solved(),
                    r.penaltyMinutes(), r.disqualified(), r.cells()));
        }
        for (LeaderboardRowResponse r : disqualified) {
            result.add(new LeaderboardRowResponse(null, r.userId(), r.username(), r.solved(),
                    r.penaltyMinutes(), r.disqualified(), r.cells()));
        }
        return result;
    }

    private LeaderboardRowResponse buildRow(Long userId,
                                           String username,
                                           Map<Long, Map<Long, List<Submission>>> byUser,
                                           List<Long> problemIds,
                                           LocalDateTime start,
                                           boolean disqualified) {
        Map<Long, LeaderboardCellResponse> cells = new LinkedHashMap<>();
        int solved = 0;
        int penalty = 0;

        for (Long problemId : problemIds) {
            List<Submission> list = byUser.getOrDefault(userId, Map.of()).get(problemId);
            if (list == null || list.isEmpty()) {
                continue;
            }
            int wrongAttempts = 0;
            Submission firstAccepted = null;
            for (Submission s : list) {
                if (s.getVerdict() == SubmissionVerdict.AC) {
                    firstAccepted = s;
                    break;
                }
                wrongAttempts++;
            }
            if (firstAccepted == null) {
                // Chưa giải được: hiển thị số lần nộp sai.
                cells.put(problemId, new LeaderboardCellResponse(false, wrongAttempts, null));
            } else {
                Integer minute = null;
                if (start != null && firstAccepted.getCreatedAt() != null) {
                    long diff = Duration.between(start, firstAccepted.getCreatedAt()).toMinutes();
                    minute = (int) Math.max(0, diff);
                }
                cells.put(problemId, new LeaderboardCellResponse(true, wrongAttempts, minute));
                solved++;
                penalty += (minute == null ? 0 : minute) + wrongAttempts * WRONG_ATTEMPT_PENALTY_MINUTES;
            }
        }

        return new LeaderboardRowResponse(null, userId, username, solved, penalty, disqualified, cells);
    }

    // ---------------- Proctoring report ----------------

    @Transactional(readOnly = true)
    public List<ProctorReportRowResponse> getProctoringReport(Long contestId) {
        contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        Map<Long, LocalDateTime> lastEventAt = new HashMap<>();
        for (ProctoringEvent e : proctoringEventRepository.findByContestIdOrderByCreatedAtDesc(contestId)) {
            if (e.getUser() == null) {
                continue;
            }
            // Danh sách đã sort DESC nên dòng đầu tiên của mỗi user là sự kiện gần nhất.
            lastEventAt.putIfAbsent(e.getUser().getId(), e.getCreatedAt());
        }

        return participantRepository.findByContestId(contestId).stream()
                .filter(p -> p.getUser() != null)
                .map(p -> new ProctorReportRowResponse(
                        p.getUser().getId(),
                        p.getUser().getUsername(),
                        p.getStatus(),
                        p.getViolationCount(),
                        lastEventAt.get(p.getUser().getId())))
                .sorted(Comparator.comparing(ProctorReportRowResponse::username))
                .collect(Collectors.toList());
    }

    @Transactional
    public void unlockParticipant(Long contestId, Long userId) {
        contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        ContestParticipant participant = participantRepository
                .findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Participant not registered in contest id: " + contestId + " with user id: " + userId));

        participant.setStatus(ParticipantStatus.REGISTERED);
        participant.setViolationCount(0);
        participant.setLastAwayStartTime(null);
        participantRepository.save(participant);
    }

    // ---------------- Finalize ----------------

    /**
     * Chốt bài thi: đánh dấu thí sinh đã nộp bài cuối cùng.
     *
     * <p>Được gọi khi thí sinh bấm nộp bài trong phòng thi, hoặc khi hết giờ và client
     * tự động chốt. Trạng thái {@code SUBMITTED} khoá việc nộp bài tiếp theo
     * (xem {@code SubmissionServiceImpl#createSubmission}).
     */
    @Transactional
    public ContestResponse finalizeSubmission(Long contestId, Long userId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        ContestParticipant participant = participantRepository
                .findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Participant not registered in contest id: " + contestId + " with user id: " + userId));

        if (participant.getStatus() == ParticipantStatus.DISQUALIFIED) {
            throw new AccessDeniedException("User has been disqualified from this contest due to proctoring violations");
        }

        // Kỳ thi đã kết thúc vẫn cho phép chốt bài (client tự động chốt khi hết giờ),
        // nhưng không cho chốt khi kỳ thi còn chưa bắt đầu.
        ContestStatus effective = contest.resolveStatus();
        if (effective == ContestStatus.SCHEDULED) {
            throw new AccessDeniedException("Contest is not currently active (status: " + effective + ")");
        }

        if (participant.getStatus() != ParticipantStatus.SUBMITTED) {
            participant.setStatus(ParticipantStatus.SUBMITTED);
            participant.setLastAwayStartTime(null);
            participantRepository.save(participant);
        }

        return toResponse(contest, userId);
    }

    // ---------------- Plagiarism ----------------

    /**
     * Đối soát đạo văn bằng Jaccard trên tập token của mã nguồn.
     * Chỉ so sánh các cặp thí sinh trong cùng kỳ thi và cùng bài toán.
     */
    @Transactional(readOnly = true)
    public List<PlagiarismResponse> getPlagiarism(Long contestId) {
        contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + contestId));

        Map<Long, String> problemTitles = contestProblemRepository.findByContestIdOrderByOrderIndexAsc(contestId).stream()
                .collect(Collectors.toMap(cp -> cp.getProblem().getId(), cp -> cp.getProblem().getTitle(), (a, b) -> a));

        // Gom mã nguồn AC gần nhất của mỗi (thí sinh, bài toán).
        Map<String, Submission> latestByUserProblem = new LinkedHashMap<>();
        for (Submission s : submissionRepository.findByContestId(contestId)) {
            if (s.getUser() == null || s.getProblem() == null || s.getVerdict() != SubmissionVerdict.AC) {
                continue;
            }
            String key = s.getUser().getId() + "|" + s.getProblem().getId();
            Submission existing = latestByUserProblem.get(key);
            if (existing == null || (s.getCreatedAt() != null && existing.getCreatedAt() != null
                    && s.getCreatedAt().isAfter(existing.getCreatedAt()))) {
                latestByUserProblem.put(key, s);
            }
        }

        List<PlagiarismResponse> results = new ArrayList<>();
        long id = 1;
        List<Map.Entry<String, Submission>> entries = new ArrayList<>(latestByUserProblem.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Submission a = entries.get(i).getValue();
                Submission b = entries.get(j).getValue();
                if (!a.getProblem().getId().equals(b.getProblem().getId())) {
                    continue;
                }
                Set<String> tokensA = tokenize(readCode(a.getCodeStorageUri()));
                Set<String> tokensB = tokenize(readCode(b.getCodeStorageUri()));
                double similarity = jaccard(tokensA, tokensB);
                if (similarity <= 0) {
                    continue;
                }
                results.add(new PlagiarismResponse(
                        id++,
                        a.getProblem().getId(),
                        problemTitles.getOrDefault(a.getProblem().getId(), a.getProblem().getTitle()),
                        a.getUser().getUsername(),
                        b.getUser().getUsername(),
                        similarity));
            }
        }
        results.sort((x, y) -> Double.compare(y.similarity(), x.similarity()));
        return results;
    }

    private String readCode(String uri) {
        if (uri == null || uri.isBlank() || storageService == null) {
            return "";
        }
        try (var in = storageService.downloadFileByUri(uri)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private Set<String> tokenize(String code) {
        Set<String> tokens = new HashSet<>();
        if (code == null) {
            return tokens;
        }
        for (String t : code.split("[^A-Za-z0-9_]+")) {
            if (t.length() > 1) {
                tokens.add(t.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return tokens;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
