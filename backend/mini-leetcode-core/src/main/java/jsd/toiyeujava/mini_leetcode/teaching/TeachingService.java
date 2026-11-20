package jsd.toiyeujava.mini_leetcode.teaching;

import jsd.toiyeujava.mini_leetcode.admin.service.AdminSubmissionService;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminSubmissionService.SubmissionRow;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StudyPlan;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StudyPlanItem;
import jsd.toiyeujava.mini_leetcode.studyplan.repository.StudyPlanRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Trang /teaching của giảng viên: số liệu chỉ tính trên nội dung do chính người đó tạo. */
@Service
public class TeachingService {

    /** Nộp sai từ chừng này lần trở lên mà chưa AC thì coi là "bị kẹt". */
    static final int STUCK_ATTEMPTS = 3;

    private final StudyPlanRepository plans;
    private final ProblemRepository problems;
    private final ContestRepository contests;
    private final SubmissionRepository submissions;
    private final AdminSubmissionService adminSubmissions;

    public TeachingService(StudyPlanRepository plans, ProblemRepository problems, ContestRepository contests,
                           SubmissionRepository submissions, AdminSubmissionService adminSubmissions) {
        this.plans = plans;
        this.problems = problems;
        this.contests = contests;
        this.submissions = submissions;
        this.adminSubmissions = adminSubmissions;
    }

    public record UpcomingContest(Long id, String title, LocalDateTime startTime, LocalDateTime endTime) {
    }

    public record Dashboard(int learners, int plans, List<SubmissionRow> recentSubmissions,
                            List<UpcomingContest> upcomingContests) {
    }

    public record Stage(Long problemId, String title) {
    }

    /** status theo thứ tự stage: DONE, STUCK, IN_PROGRESS hoặc NOT_STARTED. */
    public record LearnerRow(Long userId, String username, List<String> stages) {
    }

    public record PlanProgress(Long planId, String slug, String title, List<Stage> stages, List<LearnerRow> learners) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long userId) {
        List<StudyPlan> mine = myPlans(userId);
        List<Long> problemIds = mine.stream().flatMap(p -> stageIds(p).stream()).toList();
        Set<Long> learners = new HashSet<>();
        if (!problemIds.isEmpty()) {
            submissions.findLearnerProgress(problemIds, SubmissionVerdict.AC)
                    .forEach(r -> learners.add(((Number) r[1]).longValue()));
        }
        List<SubmissionRow> recent = adminSubmissions.list("", "", "", 0, 10, userId).items();
        List<UpcomingContest> upcoming = contests
                .findTop10ByCreatedByIdAndStartTimeAfterOrderByStartTimeAsc(userId, LocalDateTime.now()).stream()
                .map(c -> new UpcomingContest(c.getId(), c.getTitle(), c.getStartTime(), c.getEndTime())).toList();
        return new Dashboard(learners.size(), mine.size(), recent, upcoming);
    }

    /** Bảng học viên × stage cho từng plan do người này tạo. */
    @Transactional(readOnly = true)
    public List<PlanProgress> progress(Long userId) {
        List<PlanProgress> out = new ArrayList<>();
        for (StudyPlan plan : myPlans(userId)) {
            List<Long> ids = stageIds(plan);
            Map<Long, String> titles = problems.findAllById(ids).stream()
                    .collect(Collectors.toMap(Problem::getId, Problem::getTitle));
            List<Stage> stages = ids.stream().map(id -> new Stage(id, titles.getOrDefault(id, "#" + id))).toList();

            // userId -> (problemId -> status)
            Map<Long, String> names = new TreeMap<>();
            Map<Long, Map<Long, String>> status = new HashMap<>();
            if (!ids.isEmpty()) {
                for (Object[] r : submissions.findLearnerAttempts(ids, SubmissionVerdict.AC)) {
                    long problemId = ((Number) r[0]).longValue();
                    long learner = ((Number) r[1]).longValue();
                    boolean solved = ((Number) r[3]).intValue() == 1;
                    long attempts = ((Number) r[4]).longValue();
                    names.put(learner, (String) r[2]);
                    status.computeIfAbsent(learner, k -> new HashMap<>()).put(problemId,
                            solved ? "DONE" : attempts >= STUCK_ATTEMPTS ? "STUCK" : "IN_PROGRESS");
                }
            }
            List<LearnerRow> rows = names.entrySet().stream()
                    .map(e -> new LearnerRow(e.getKey(), e.getValue(), ids.stream()
                            .map(id -> status.get(e.getKey()).getOrDefault(id, "NOT_STARTED")).toList()))
                    .sorted(Comparator.comparing(LearnerRow::username))
                    .toList();
            out.add(new PlanProgress(plan.getId(), plan.getSlug(), plan.getTitle(), stages, rows));
        }
        return out;
    }

    private List<StudyPlan> myPlans(Long userId) {
        return plans.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> p.getCreatedBy() != null && userId.equals(p.getCreatedBy().getId()))
                .toList();
    }

    /** Mọi đề của plan theo thứ tự item rồi stage. */
    private static List<Long> stageIds(StudyPlan plan) {
        return plan.getItems().stream()
                .sorted(Comparator.comparingInt(StudyPlanItem::getSortOrder))
                .flatMap(i -> i.getProblemIds().stream())
                .distinct()
                .toList();
    }
}
