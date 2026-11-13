package jsd.toiyeujava.mini_leetcode.contest.admin;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminContestDetail;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminContestProblem;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminContestSummary;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.AdminParticipant;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.BulkOutcome;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.BulkParticipantLine;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.BulkParticipantsResult;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.ContestProblemInput;
import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.ContestUpsertRequest;
import jsd.toiyeujava.mini_leetcode.contest.dto.LeaderboardRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.contest.service.ContestService;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.RegistrationMode;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemService;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Quản lý kỳ thi cho giảng viên/admin.
 *
 * <p>Mọi luật khoá theo trạng thái được kiểm tra ở đây, theo {@link Contest#resolveStatus()} tại thời
 * điểm request — không tin vào những gì giao diện thấy lần trước:
 * <ul>
 *   <li>SCHEDULED: sửa mọi thứ.</li>
 *   <li>RUNNING: chỉ được lùi giờ kết thúc.</li>
 *   <li>COMPLETED: chỉ xem.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContestAdminService {

    static final String RUNNING_LOCKED = "Only the end time can be changed while the contest is running";
    static final String ONLY_EXTEND = "The end time can only be extended while the contest is running";
    static final String ENDED = "Contest has ended";
    static final String STALE = "Contest was changed by someone else, reload";
    static final String DELETE_RULE = "Only scheduled contests without submissions can be deleted";
    static final String NEEDS_PROBLEM = "A contest needs at least one problem to start";
    static final int MAX_BULK_ENTRIES = 500;
    static final int MAX_VIOLATIONS_LIMIT = 100;

    private static final Pattern LABEL = Pattern.compile("[A-Z0-9]{1,3}");
    private static final Pattern ENTRY_SEPARATOR = Pattern.compile("[,;\\r\\n]+");

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository participantRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ContestService contestService;
    private final EntityManager entityManager;

    // ---------------- Đọc ----------------

    @Transactional(readOnly = true)
    public List<AdminContestSummary> list(ContestStatus status, String search) {
        String needle = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        List<AdminContestSummary> result = new ArrayList<>();
        for (Object[] row : contestRepository.findAllWithCounts()) {
            Contest c = (Contest) row[0];
            ContestStatus effective = c.resolveStatus();
            if (status != null && effective != status) {
                continue;
            }
            if (needle != null && !c.getTitle().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(new AdminContestSummary(c.getId(), c.getTitle(), c.getSlug(), effective,
                    c.getStartTime(), c.getEndTime(), c.getRegistrationMode(),
                    ((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        // Đang thi → sắp tới (gần nhất trước) → đã kết thúc (mới nhất trước).
        result.sort(Comparator
                .comparingInt((AdminContestSummary s) -> switch (s.status()) {
                    case RUNNING -> 0;
                    case SCHEDULED -> 1;
                    case COMPLETED -> 2;
                })
                .thenComparing((a, b) -> a.status() == ContestStatus.COMPLETED
                        ? b.startTime().compareTo(a.startTime())
                        : a.startTime().compareTo(b.startTime())));
        return result;
    }

    @Transactional(readOnly = true)
    public AdminContestDetail get(Long contestId) {
        return toDetail(load(contestId));
    }

    // ---------------- Tạo / sửa ----------------

    @Transactional
    public AdminContestDetail create(ContestUpsertRequest request, Long creatorId) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + creatorId));

        String title = requireTitle(request.title());
        LocalDateTime start = requireTime(request.startTime(), "Start time");
        LocalDateTime end = requireTime(request.endTime(), "End time");
        requireEndAfterStart(start, end);
        if (!start.isAfter(now())) {
            throw new IllegalArgumentException("Start time must be in the future");
        }
        List<ContestProblemInput> problems = normalizeProblems(request.problems());
        Map<Long, Problem> byId = loadPublished(problems);

        Contest contest = contestRepository.save(Contest.builder()
                .title(title)
                .slug(uniqueSlug(title))
                .startTime(start)
                .endTime(end)
                .scoringPolicy(request.scoringPolicy() == null ? ScoringPolicy.ACM_ICPC : request.scoringPolicy())
                .maxViolationsAllowed(validViolations(request.maxViolationsAllowed(), 3))
                .registrationMode(request.registrationMode() == null ? RegistrationMode.OPEN : request.registrationMode())
                .status(ContestStatus.SCHEDULED)
                .createdBy(creator)
                .build());
        saveProblems(contest, problems, byId);
        return toDetail(contest);
    }

    @Transactional
    public AdminContestDetail update(Long contestId, ContestUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Contest contest = load(contestId);
        if (request.version() != null && !request.version().equals(contest.getVersion())) {
            throw new StateConflictException(STALE);
        }
        switch (contest.resolveStatus()) {
            case COMPLETED -> throw new StateConflictException(ENDED);
            case RUNNING -> applyRunningEdit(contest, request);
            case SCHEDULED -> applyScheduledEdit(contest, request);
        }
        // Luôn tăng version (kể cả khi chỉ đổi danh sách bài) và flush ngay, để xung đột @Version
        // giữa hai người cùng lưu nổ ra trong request này và thành 409.
        entityManager.lock(contest, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        contestRepository.saveAndFlush(contest);
        return toDetail(contest);
    }

    private void applyScheduledEdit(Contest contest, ContestUpsertRequest request) {
        String title = request.title() == null ? contest.getTitle() : requireTitle(request.title());
        LocalDateTime start = request.startTime() == null ? contest.getStartTime() : toUtc(request.startTime());
        LocalDateTime end = request.endTime() == null ? contest.getEndTime() : toUtc(request.endTime());
        requireEndAfterStart(start, end);
        if (!sameMinute(start, contest.getStartTime()) && !start.isAfter(now())) {
            throw new IllegalArgumentException("Start time must be in the future");
        }

        if (request.problems() != null) {
            List<ContestProblemInput> problems = normalizeProblems(request.problems());
            Map<Long, Problem> byId = loadPublished(problems);
            contestProblemRepository.deleteByContestId(contest.getId());
            saveProblems(contest, problems, byId);
        }

        contest.setTitle(title);
        contest.setStartTime(start);
        contest.setEndTime(end);
        if (request.scoringPolicy() != null) {
            contest.setScoringPolicy(request.scoringPolicy());
        }
        contest.setMaxViolationsAllowed(validViolations(request.maxViolationsAllowed(), contest.getMaxViolationsAllowed()));
        if (request.registrationMode() != null) {
            contest.setRegistrationMode(request.registrationMode());
        }
    }

    /** Đang thi: mọi trường khác phải giữ nguyên; giờ kết thúc chỉ được lùi ra sau. */
    private void applyRunningEdit(Contest contest, ContestUpsertRequest request) {
        boolean otherChange =
                (request.title() != null && !request.title().trim().equals(contest.getTitle()))
                        || (request.startTime() != null && !sameMinute(toUtc(request.startTime()), contest.getStartTime()))
                        || (request.scoringPolicy() != null && request.scoringPolicy() != contest.getScoringPolicy())
                        || (request.maxViolationsAllowed() != null
                                && !request.maxViolationsAllowed().equals(contest.getMaxViolationsAllowed()))
                        || (request.registrationMode() != null && request.registrationMode() != contest.getRegistrationMode())
                        || (request.problems() != null && !sameProblems(contest, request.problems()));
        if (otherChange) {
            throw new StateConflictException(RUNNING_LOCKED);
        }
        if (request.endTime() == null) {
            return;
        }
        LocalDateTime end = toUtc(request.endTime());
        if (sameMinute(end, contest.getEndTime())) {
            return;
        }
        if (end.isBefore(contest.getEndTime())) {
            throw new StateConflictException(ONLY_EXTEND);
        }
        if (contestProblemRepository.findByContestIdOrderByOrderIndexAsc(contest.getId()).isEmpty()) {
            throw new StateConflictException(NEEDS_PROBLEM);
        }
        contest.setEndTime(end);
    }

    private boolean sameProblems(Contest contest, List<ContestProblemInput> requested) {
        List<ContestProblemInput> normalized = normalizeProblems(requested);
        List<ContestProblem> current = contestProblemRepository.findByContestIdOrderByOrderIndexAsc(contest.getId());
        if (normalized.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            ContestProblem cp = current.get(i);
            ContestProblemInput in = normalized.get(i);
            if (!cp.getProblem().getId().equals(in.problemId()) || !cp.getLabel().equals(in.label())) {
                return false;
            }
        }
        return true;
    }

    // ---------------- Xoá ----------------

    @Transactional
    public void delete(Long contestId) {
        Contest contest = load(contestId);
        if (contest.resolveStatus() != ContestStatus.SCHEDULED || submissionRepository.countByContestId(contestId) > 0) {
            throw new StateConflictException(DELETE_RULE);
        }
        participantRepository.deleteByContestId(contestId);
        contestProblemRepository.deleteByContestId(contestId);
        contestRepository.delete(contest);
    }

    // ---------------- Thí sinh ----------------

    @Transactional(readOnly = true)
    public List<AdminParticipant> participants(Long contestId) {
        load(contestId);
        Map<Long, LeaderboardRowResponse> rows = contestService.getLeaderboard(contestId).stream()
                .filter(r -> r.userId() != null)
                .collect(Collectors.toMap(LeaderboardRowResponse::userId, Function.identity(), (a, b) -> a));
        Set<Long> withSubmissions = new HashSet<>(submissionRepository.findUserIdsByContestId(contestId));

        return participantRepository.findWithUserByContestIdIn(List.of(contestId)).stream()
                .map(p -> {
                    User u = p.getUser();
                    LeaderboardRowResponse row = rows.get(u.getId());
                    return new AdminParticipant(u.getId(), u.getUsername(), u.getEmail(), p.getStatus(),
                            p.getViolationCount() == null ? 0 : p.getViolationCount(),
                            row == null ? 0 : row.solved(),
                            row == null ? 0 : row.penaltyMinutes(),
                            row == null ? null : row.rank(),
                            withSubmissions.contains(u.getId()));
                })
                .sorted(Comparator.comparing(AdminParticipant::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public BulkParticipantsResult addParticipants(Long contestId, String entries) {
        Contest contest = load(contestId);
        if (contest.resolveStatus() == ContestStatus.COMPLETED) {
            throw new StateConflictException(ENDED);
        }
        List<String> inputs = parseEntries(entries);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one username or email");
        }
        if (inputs.size() > MAX_BULK_ENTRIES) {
            throw new IllegalArgumentException("At most " + MAX_BULK_ENTRIES + " entries per request");
        }

        // Hai truy vấn cho cả danh sách, không truy vấn theo từng dòng.
        Map<String, User> byUsername = new HashMap<>();
        userRepository.findByUsernameIn(inputs)
                .forEach(u -> byUsername.put(u.getUsername().toLowerCase(Locale.ROOT), u));
        List<String> emails = inputs.stream().filter(s -> s.contains("@")).toList();
        Map<String, User> byEmail = new HashMap<>();
        if (!emails.isEmpty()) {
            userRepository.findByEmailIn(emails).forEach(u -> byEmail.put(u.getEmail().toLowerCase(Locale.ROOT), u));
        }
        Set<Long> registered = participantRepository.findByContestId(contestId).stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toCollection(HashSet::new));

        List<BulkParticipantLine> lines = new ArrayList<>();
        List<ContestParticipant> toSave = new ArrayList<>();
        for (String input : inputs) {
            String key = input.toLowerCase(Locale.ROOT);
            User user = byUsername.containsKey(key) ? byUsername.get(key) : byEmail.get(key);
            BulkOutcome outcome;
            if (user == null) {
                outcome = BulkOutcome.NOT_FOUND;
            } else if (user.getStatus() == UserStatus.BLOCKED) {
                outcome = BulkOutcome.BLOCKED;
            } else if (!registered.add(user.getId())) {
                outcome = BulkOutcome.ALREADY_REGISTERED;
            } else {
                outcome = BulkOutcome.ADDED;
                toSave.add(ContestParticipant.builder()
                        .contest(contest)
                        .user(user)
                        .status(ParticipantStatus.REGISTERED)
                        .violationCount(0)
                        .score(0)
                        .penaltyTimeMinutes(0)
                        .build());
            }
            lines.add(new BulkParticipantLine(input, outcome, user == null ? null : user.getUsername()));
        }
        participantRepository.saveAll(toSave);
        return new BulkParticipantsResult(lines, toSave.size());
    }

    @Transactional
    public void removeParticipant(Long contestId, Long userId) {
        Contest contest = load(contestId);
        if (contest.resolveStatus() == ContestStatus.COMPLETED) {
            throw new StateConflictException(ENDED);
        }
        ContestParticipant participant = participantRepository.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        if (submissionRepository.countByContestIdAndUserId(contestId, userId) > 0) {
            throw new StateConflictException("Participant has submissions in this contest and cannot be removed");
        }
        participantRepository.delete(participant);
    }

    // ---------------- Hỗ trợ ----------------

    /** Tách danh sách dán vào: mỗi dòng, dấu phẩy hoặc chấm phẩy; bỏ dòng trống. */
    static List<String> parseEntries(String entries) {
        if (entries == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : ENTRY_SEPARATOR.split(entries)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * Chuẩn hoá danh sách bài: sắp theo orderIndex (thiếu thì theo vị trí), đánh lại 0..n-1,
     * gán nhãn A, B, C… cho dòng bỏ trống; nhãn 1–3 ký tự chữ/số, không trùng; không trùng bài.
     */
    static List<ContestProblemInput> normalizeProblems(List<ContestProblemInput> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<ContestProblemInput> indexed = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ContestProblemInput p = input.get(i);
            if (p == null || p.problemId() == null) {
                throw new IllegalArgumentException("Each contest problem needs a problemId");
            }
            indexed.add(new ContestProblemInput(p.problemId(), p.label(), p.orderIndex() == null ? i : p.orderIndex()));
        }
        indexed.sort(Comparator.comparingInt(ContestProblemInput::orderIndex));

        Set<Long> seenProblems = new HashSet<>();
        Set<String> seenLabels = new HashSet<>();
        List<ContestProblemInput> out = new ArrayList<>();
        for (int i = 0; i < indexed.size(); i++) {
            ContestProblemInput p = indexed.get(i);
            if (!seenProblems.add(p.problemId())) {
                throw new IllegalArgumentException("Problem " + p.problemId() + " is listed more than once");
            }
            String label = StringUtils.hasText(p.label()) ? p.label().trim().toUpperCase(Locale.ROOT) : defaultLabel(i);
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Label '" + p.label() + "' must be 1-3 letters or digits");
            }
            if (!seenLabels.add(label)) {
                throw new IllegalArgumentException("Label '" + label + "' is used more than once");
            }
            out.add(new ContestProblemInput(p.problemId(), label, i));
        }
        return out;
    }

    /** A..Z, rồi AA, AB… */
    static String defaultLabel(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private Map<Long, Problem> loadPublished(List<ContestProblemInput> problems) {
        if (problems.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = problems.stream().map(ContestProblemInput::problemId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Problem> byId = problemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        for (Long id : ids) {
            Problem p = byId.get(id);
            if (p == null) {
                throw new ResourceNotFoundException("Problem not found with id: " + id);
            }
            if (p.getStatus() != ProblemStatus.PUBLISHED) {
                throw new IllegalArgumentException("Problem '" + p.getTitle() + "' is not published");
            }
        }
        return byId;
    }

    private void saveProblems(Contest contest, List<ContestProblemInput> problems, Map<Long, Problem> byId) {
        List<ContestProblem> rows = problems.stream()
                .map(p -> ContestProblem.builder()
                        .contest(contest)
                        .problem(byId.get(p.problemId()))
                        .label(p.label())
                        .orderIndex(p.orderIndex())
                        .build())
                .toList();
        contestProblemRepository.saveAll(rows);
    }

    private AdminContestDetail toDetail(Contest c) {
        List<AdminContestProblem> problems = contestProblemRepository.findWithProblemByContestId(c.getId()).stream()
                .map(cp -> new AdminContestProblem(cp.getProblem().getId(), cp.getProblem().getSlug(),
                        cp.getProblem().getTitle(), cp.getProblem().getDifficulty(), cp.getProblem().getStatus(),
                        cp.getLabel(), cp.getOrderIndex()))
                .toList();
        return new AdminContestDetail(c.getId(), c.getTitle(), c.getSlug(), c.resolveStatus(),
                c.getStartTime(), c.getEndTime(), c.getScoringPolicy(), c.getMaxViolationsAllowed(),
                c.getRegistrationMode(),
                c.getCreatedBy() == null ? null : c.getCreatedBy().getUsername(),
                c.getVersion(),
                submissionRepository.countByContestId(c.getId()) > 0,
                problems);
    }

    private Contest load(Long contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
    }

    private String uniqueSlug(String title) {
        String base = ProblemService.toSlug(title);
        if ("problem".equals(base)) {
            base = "contest";
        }
        String candidate = base;
        int n = 1;
        while (contestRepository.existsBySlug(candidate)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private static String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("Title must be at most 200 characters");
        }
        return trimmed;
    }

    private static LocalDateTime requireTime(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return toUtc(value);
    }

    private static void requireEndAfterStart(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
    }

    private static int validViolations(Integer value, int fallback) {
        int v = value == null ? fallback : value;
        if (v < 0 || v > MAX_VIOLATIONS_LIMIT) {
            throw new IllegalArgumentException("Max violations must be between 0 and " + MAX_VIOLATIONS_LIMIT);
        }
        return v;
    }

    /** Ô nhập giờ trên UI chỉ tới phút; so sánh theo phút để không coi lệch giây là "đã sửa". */
    private static boolean sameMinute(LocalDateTime a, LocalDateTime b) {
        return Objects.equals(a == null ? null : a.truncatedTo(ChronoUnit.MINUTES),
                b == null ? null : b.truncatedTo(ChronoUnit.MINUTES));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
