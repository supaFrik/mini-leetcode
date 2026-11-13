package jsd.toiyeujava.mini_leetcode.studyplan.service;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.StageStatus;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanItemKind;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.DuplicateResourceException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemService;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStep;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;
import jsd.toiyeujava.mini_leetcode.studyplan.dto.StudyPlanDtos.*;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StudyPlan;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StageTopic;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StudyPlanItem;
import jsd.toiyeujava.mini_leetcode.studyplan.repository.StudyPlanRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.topic.dto.TopicDtos.TopicRef;
import jsd.toiyeujava.mini_leetcode.topic.entity.Topic;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicRepository;
import jsd.toiyeujava.mini_leetcode.topic.service.TopicService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lộ trình học (specs/study-plan.spec.md). Tiến độ không lưu: suy ra từ submissions chính thức
 * bằng 2 truy vấn có sẵn (đề đã AC, đề đã nộp) cho mỗi lần gọi.
 */
@Service
public class StudyPlanService {

    private final StudyPlanRepository planRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final TopicService topicService;
    private final ProblemService problemService;
    private final TestCaseRepository testCaseRepository;

    public StudyPlanService(StudyPlanRepository planRepository, ProblemRepository problemRepository,
                            SubmissionRepository submissionRepository, UserRepository userRepository,
                            TopicRepository topicRepository, TopicService topicService,
                            ProblemService problemService, TestCaseRepository testCaseRepository) {
        this.planRepository = planRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.topicService = topicService;
        this.problemService = problemService;
        this.testCaseRepository = testCaseRepository;
    }

    /** Học viên chỉ thấy plan PUBLISHED; giảng viên / admin thấy mọi plan. */
    @Transactional(readOnly = true)
    public List<PlanSummary> list(Long userId, boolean staff) {
        List<StudyPlan> plans = staff ? planRepository.findAllByOrderByCreatedAtDesc()
                : planRepository.findByStatusOrderByCreatedAtDesc(StudyPlanStatus.PUBLISHED);
        Progress progress = progressOf(userId, staff);
        // ponytail: duyệt items/stages lazy theo từng plan (N+1 theo số plan) — đủ nhanh với vài chục plan.
        return plans.stream().map(p -> {
            PlanDetail d = toDetail(p, progress, Map.of(), Map.of(), null);
            return new PlanSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.getStatus(),
                    p.getItems().size(), d.stageCount(), d.doneStages(), d.createdById(), d.createdByUsername());
        }).toList();
    }

    @Transactional(readOnly = true)
    public PlanDetail get(String slug, Long userId, boolean staff) {
        StudyPlan plan = findVisible(slug, staff);
        List<Long> ids = plan.getItems().stream().flatMap(i -> i.getProblemIds().stream()).toList();
        Map<Long, Problem> problems = problemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        List<Long> topicIds = plan.getItems().stream()
                .flatMap(i -> i.getStageTopics().stream()).map(StageTopic::getTopicId).toList();
        return toDetail(plan, progressOf(userId, staff), problems, topicService.refs(topicIds, userId, staff),
                staff ? learnersOf(ids) : null);
    }

    @Transactional
    public PlanDetail create(UpsertPlanRequest request, Long userId) {
        if (planRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Study plan slug already exists: " + request.slug());
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        StudyPlan plan = StudyPlan.builder().slug(request.slug()).createdBy(author).build();
        apply(plan, request);
        planRepository.save(plan);
        return get(plan.getSlug(), userId, true);
    }

    /** Thay toàn bộ nội dung plan (tiến độ không mất vì suy ra từ submissions). */
    @Transactional
    public PlanDetail update(String slug, UpsertPlanRequest request, Long userId, Role role) {
        StudyPlan plan = findOwned(slug, userId, role);
        if (!plan.getSlug().equals(request.slug()) && planRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Study plan slug already exists: " + request.slug());
        }
        if (request.expectedUpdatedAt() != null && plan.getUpdatedAt() != null
                && !sameSecond(request.expectedUpdatedAt(), plan.getUpdatedAt())) {
            throw new StateConflictException("This study plan was changed in another tab or by another user. Reload to continue.");
        }
        if (plan.getStatus() == StudyPlanStatus.PUBLISHED) {
            requireCompleteItems(request);
        }
        plan.setSlug(request.slug());
        plan.getItems().clear();
        planRepository.flush(); // xoá stage cũ trước để UNIQUE(problem_id) không chặn chính các đề này
        apply(plan, request);
        plan.setUpdatedAt(LocalDateTime.now()); // đổi mục không làm plan "dirty" ⇒ tự đánh dấu cho khoá lạc quan
        planRepository.save(plan);
        return get(plan.getSlug(), userId, true);
    }

    @Transactional
    public PlanDetail transition(String slug, StudyPlanStatus target, Long userId, Role role) {
        StudyPlan plan = findOwned(slug, userId, role);
        if (target == StudyPlanStatus.PUBLISHED && plan.getItems().isEmpty()) {
            throw new StateConflictException("A study plan needs at least one item before it can be published");
        }
        if (target == StudyPlanStatus.PUBLISHED) {
            plan.getItems().stream().filter(i -> i.getProblemIds().isEmpty()).findFirst().ifPresent(i -> {
                throw new StateConflictException("Item \"" + i.getTitle() + "\" needs at least one problem before publishing");
            });
        }
        plan.setStatus(target);
        return get(plan.getSlug(), userId, true);
    }

    @Transactional
    public void delete(String slug, Long userId, Role role) {
        planRepository.delete(findOwned(slug, userId, role));
    }

    // ------------------------------------------------------------------ publish (specs/course-builder.spec.md)

    /** Checklist trước khi publish: lỗi chặn publish, cảnh báo thì không. Không đổi dữ liệu. */
    @Transactional(readOnly = true)
    public PublishCheck publishCheck(String slug, Long userId, Role role) {
        return check(findOwned(slug, userId, role), userId, role);
    }

    /**
     * Publish plan cùng mọi đề / chủ đề chưa PUBLISHED bên trong, trong một transaction: lỗi ở bất kỳ bước nào
     * thì không thực thể nào đổi trạng thái.
     */
    @Transactional
    public PlanDetail publish(String slug, Long userId, Role role) {
        StudyPlan plan = findOwned(slug, userId, role);
        PublishCheck check = check(plan, userId, role);
        if (!check.ok()) {
            throw new StateConflictException("Cannot publish: " + check.errors().get(0).message());
        }
        Map<Long, Problem> problems = problemRepository.findAllById(check.problemsToPublish()).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        for (Long id : check.problemsToPublish()) {
            List<ProblemStatus> path = switch (problems.get(id).getStatus()) {
                case ARCHIVED -> List.of(ProblemStatus.DRAFT, ProblemStatus.REVIEWING, ProblemStatus.PUBLISHED);
                case DRAFT -> List.of(ProblemStatus.REVIEWING, ProblemStatus.PUBLISHED);
                case REVIEWING -> List.of(ProblemStatus.PUBLISHED);
                case PUBLISHED -> List.<ProblemStatus>of();
            };
            for (ProblemStatus target : path) {
                problemService.transitionStatus(id, target);
            }
        }
        for (Long topicId : check.topicsToPublish()) {
            Topic topic = topicRepository.findById(topicId).orElseThrow();
            topicService.transition(topic.getSlug(), StudyPlanStatus.PUBLISHED, userId, role);
        }
        plan.setStatus(StudyPlanStatus.PUBLISHED);
        return get(plan.getSlug(), userId, true);
    }

    private PublishCheck check(StudyPlan plan, Long userId, Role role) {
        List<PublishIssue> errors = new ArrayList<>();
        List<PublishIssue> warnings = new ArrayList<>();
        if (plan.getItems().isEmpty()) {
            errors.add(new PublishIssue("plan", null, null, null, "Add at least one project or practice problem"));
        }
        List<Long> problemIds = plan.getItems().stream().flatMap(i -> i.getProblemIds().stream()).toList();
        Map<Long, Problem> problems = problemRepository.findAllById(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        Set<Long> problemsToPublish = new LinkedHashSet<>();
        for (int index = 0; index < plan.getItems().size(); index++) {
            StudyPlanItem item = plan.getItems().get(index);
            boolean project = item.getKind() == StudyPlanItemKind.PROJECT;
            if (item.getProblemIds().isEmpty()) {
                errors.add(new PublishIssue("item", index, null, null,
                        "\"" + item.getTitle() + "\" has no " + (project ? "stage" : "problem")));
            }
            for (int stage = 0; stage < item.getProblemIds().size(); stage++) {
                Problem p = problems.get(item.getProblemIds().get(stage));
                String where = project ? item.getTitle() + " · stage " + (stage + 1) : item.getTitle();
                if (p.getStatus() != ProblemStatus.PUBLISHED) {
                    problemsToPublish.add(p.getId());
                }
                if (p.getDescription() == null || p.getDescription().isBlank()) {
                    errors.add(new PublishIssue("stage", index, p.getId(), null, where + ": the statement is empty"));
                }
                if (testCaseRepository.countByProblemIdAndIsHiddenFalse(p.getId()) == 0) {
                    errors.add(new PublishIssue("stage", index, p.getId(), null, where + ": add at least one public test case"));
                }
                String reference = p.getReferenceSolution();
                if (p.getProblemType() == ProblemType.OOP_GRINDING
                        && (reference == null || reference.isBlank() || reference.trim().equals("[]"))) {
                    warnings.add(new PublishIssue("stage", index, p.getId(), null,
                            where + ": no reference solution, expected outputs are not verified"));
                }
            }
        }

        Set<Long> topicIds = plan.getItems().stream().flatMap(i -> i.getStageTopics().stream())
                .map(StageTopic::getTopicId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Topic> topics = topicIds.isEmpty() ? List.of() : topicRepository.findAllById(topicIds);
        Set<Long> topicsToPublish = new LinkedHashSet<>();
        for (Topic topic : topics) {
            if (topic.getStatus() == StudyPlanStatus.PUBLISHED) {
                continue;
            }
            if (role != Role.ADMIN && !topic.getCreatedBy().getId().equals(userId)) {
                errors.add(new PublishIssue("topic", null, null, topic.getId(), "Topic \"" + topic.getTitle()
                        + "\" is a draft by " + topic.getCreatedBy().getUsername() + "; only its author can publish it"));
                continue;
            }
            if (topic.getSteps().isEmpty()) {
                errors.add(new PublishIssue("topic", null, null, topic.getId(), "Topic \"" + topic.getTitle() + "\" has no steps"));
            }
            topicsToPublish.add(topic.getId());
        }
        // Step CODE trỏ tới đề phải PUBLISHED, hoặc sẽ được publish cùng plan.
        for (Topic topic : topics) {
            for (TopicStep step : topic.getSteps()) {
                if (step.getType() != TopicStepType.CODE || step.getProblemId() == null
                        || problemsToPublish.contains(step.getProblemId())) {
                    continue;
                }
                Problem p = problems.containsKey(step.getProblemId()) ? problems.get(step.getProblemId())
                        : problemRepository.findById(step.getProblemId()).orElse(null);
                if (p != null && p.getStatus() != ProblemStatus.PUBLISHED) {
                    errors.add(new PublishIssue("topic", null, null, topic.getId(), "Topic \"" + topic.getTitle()
                            + "\": code step \"" + step.getTitle() + "\" uses unpublished problem " + p.getSlug()));
                }
            }
        }
        return new PublishCheck(errors.isEmpty(), errors, warnings,new ArrayList<>(problemsToPublish), new ArrayList<>(topicsToPublish));
    }

    private static void requireCompleteItems(UpsertPlanRequest request) {
        if (request.items().isEmpty()) {
            throw new StateConflictException("A published study plan must keep at least one item");
        }
        request.items().stream().filter(i -> i.problemIds().isEmpty()).findFirst().ifPresent(i -> {
            throw new StateConflictException("Item \"" + i.title() + "\" of a published study plan needs at least one problem");
        });
    }

    private static boolean sameSecond(LocalDateTime a, LocalDateTime b) {
        return a.truncatedTo(ChronoUnit.SECONDS).equals(b.truncatedTo(ChronoUnit.SECONDS));
    }

    /** Số học viên (USER) của plan và số người đang làm dở (đã nộp, chưa AC) từng stage. */
    private record Learners(int total, Map<Long, Integer> onStage) {}

    private Learners learnersOf(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return new Learners(0, Map.of());
        }
        Set<Long> users = new HashSet<>();
        Map<Long, Integer> onStage = new HashMap<>();
        for (Object[] row : submissionRepository.findLearnerProgress(problemIds, SubmissionVerdict.AC)) {
            long problemId = ((Number) row[0]).longValue();
            users.add(((Number) row[1]).longValue());
            if (((Number) row[2]).intValue() == 0) {
                onStage.merge(problemId, 1, Integer::sum);
            }
        }
        return new Learners(users.size(), onStage);
    }

    // ------------------------------------------------------------------

    /**
     * Đề ở mọi trạng thái đều gắn được (Course Builder soạn đề ngay trong plan). Đề chưa PUBLISHED trong plan đang
     * PUBLISHED là stage "đang cập nhật": học viên không nộp được (SubmissionServiceImpl) cho tới khi publish.
     */
    private void apply(StudyPlan plan, UpsertPlanRequest request) {
        plan.setTitle(request.title());
        plan.setDescription(request.description());

        List<Long> allIds = request.items().stream().flatMap(i -> i.problemIds().stream()).toList();
        if (new HashSet<>(allIds).size() != allIds.size()) {
            throw new IllegalArgumentException("A problem can appear only once in a study plan");
        }
        Map<Long, Problem> problems = problemRepository.findAllById(allIds).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        for (Long id : allIds) {
            Problem p = problems.get(id);
            if (p == null) {
                throw new ResourceNotFoundException("Problem not found with id: " + id);
            }
        }
        List<Number> taken = allIds.isEmpty() ? List.of() : planRepository.findProblemIdsAlreadyInPlans(allIds);
        if (!taken.isEmpty()) {
            throw new StateConflictException("Problem already belongs to another study plan item: "
                    + problems.get(taken.get(0).longValue()).getSlug());
        }

        Set<Long> topicIds = request.items().stream().filter(i -> i.stageTopics() != null)
                .flatMap(i -> i.stageTopics().stream()).filter(java.util.Objects::nonNull)
                .flatMap(List::stream).collect(Collectors.toSet());
        Set<Long> existingTopics = topicIds.isEmpty() ? Set.of() : topicRepository.findAllById(topicIds).stream()
                .map(Topic::getId).collect(Collectors.toSet());
        topicIds.stream().filter(id -> !existingTopics.contains(id)).findFirst().ifPresent(id -> {
            throw new ResourceNotFoundException("Topic not found with id: " + id);
        });

        int position = 0;
        for (ItemRequest itemRequest : request.items()) {
            if (itemRequest.kind() == StudyPlanItemKind.PROBLEM && itemRequest.problemIds().size() > 1) {
                throw new IllegalArgumentException("A PROBLEM item must contain exactly one problem");
            }
            List<StageTopic> stageTopics = new ArrayList<>();
            if (itemRequest.stageTopics() != null) {
                if (itemRequest.stageTopics().size() > itemRequest.problemIds().size()) {
                    throw new IllegalArgumentException("stageTopics has more entries than the item has stages: "
                            + itemRequest.title());
                }
                for (int stage = 0; stage < itemRequest.stageTopics().size(); stage++) {
                    List<Long> ids = itemRequest.stageTopics().get(stage);
                    if (ids == null) {
                        continue;
                    }
                    if (new HashSet<>(ids).size() != ids.size()) {
                        throw new IllegalArgumentException("A topic can appear only once in a stage: " + itemRequest.title());
                    }
                    for (Long id : ids) {
                        stageTopics.add(new StageTopic(stage, id));
                    }
                }
            }
            plan.getItems().add(StudyPlanItem.builder()
                    .plan(plan)
                    .sortOrder(position++)
                    .kind(itemRequest.kind())
                    .title(itemRequest.title())
                    .description(itemRequest.description())
                    .problemIds(new ArrayList<>(itemRequest.problemIds()))
                    .stageTopics(stageTopics)
                    .build());
        }
    }

    private StudyPlan findVisible(String slug, boolean staff) {
        return planRepository.findBySlug(slug)
                .filter(p -> staff || p.getStatus() == StudyPlanStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found: " + slug));
    }

    private StudyPlan findOwned(String slug, Long userId, Role role) {
        StudyPlan plan = planRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found: " + slug));
        if (role != Role.ADMIN && !plan.getCreatedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Only the author or an admin can modify this study plan");
        }
        return plan;
    }

    private record Progress(Set<Long> solved, Set<Long> attempted, boolean staff) {}

    private Progress progressOf(Long userId, boolean staff) {
        return new Progress(
                new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(userId, SubmissionVerdict.AC)),
                new HashSet<>(submissionRepository.findAttemptedProblemIdsByUserId(userId)),
                staff);
    }

    /**
     * {@code problems} rỗng ⇒ bỏ thông tin đề (dùng cho danh sách, chỉ cần đếm). {@code topics}: chủ đề người xem
     * được thấy (chủ đề DRAFT bị ẩn với học viên vì không có trong map).
     */
    private PlanDetail toDetail(StudyPlan plan, Progress progress, Map<Long, Problem> problems,
                                Map<Long, TopicRef> topics, Learners learners) {
        List<Item> items = new ArrayList<>();
        int stageCount = 0;
        int done = 0;
        for (StudyPlanItem item : plan.getItems()) {
            List<Stage> stages = new ArrayList<>();
            boolean previousDone = true;
            for (int i = 0; i < item.getProblemIds().size(); i++) {
                Long problemId = item.getProblemIds().get(i);
                StageStatus status;
                if (progress.solved().contains(problemId)) {
                    status = StageStatus.DONE;
                } else if (!previousDone && !progress.staff()) {
                    status = StageStatus.LOCKED; // khớp với khoá trong SubmissionServiceImpl
                } else {
                    status = progress.attempted().contains(problemId) ? StageStatus.IN_PROGRESS : StageStatus.NEVER;
                }
                previousDone = status == StageStatus.DONE;
                Problem p = problems.get(problemId);
                int stageNo = i;
                List<TopicRef> stageTopics = item.getStageTopics().stream()
                        .filter(t -> t.getStageNo() == stageNo).map(t -> topics.get(t.getTopicId()))
                        .filter(java.util.Objects::nonNull).toList();
                stages.add(new Stage(i + 1, problemId,
                        p == null ? null : p.getSlug(), p == null ? null : p.getTitle(),
                        p == null ? null : p.getDifficulty(), p == null ? null : p.getProblemType(),
                        p != null && p.getStatus() == ProblemStatus.PUBLISHED, status, stageTopics,
                        p == null || !progress.staff() ? null : p.getStatus(),
                        learners == null ? null : learners.onStage().getOrDefault(problemId, 0)));
            }
            long itemDone = stages.stream().filter(s -> s.status() == StageStatus.DONE).count();
            stageCount += stages.size();
            done += (int) itemDone;
            items.add(new Item(item.getId(), item.getSortOrder(), item.getKind(), item.getTitle(),
                    item.getDescription(), !stages.isEmpty() && itemDone == stages.size(), stages));
        }
        return new PlanDetail(plan.getId(), plan.getSlug(), plan.getTitle(), plan.getDescription(),
                plan.getStatus(), stageCount, done, items,
                plan.getCreatedBy().getId(), plan.getCreatedBy().getUsername(),
                plan.getUpdatedAt(), learners == null ? null : learners.total());
    }
}
