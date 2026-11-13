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

import java.util.ArrayList;
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

    public StudyPlanService(StudyPlanRepository planRepository, ProblemRepository problemRepository,
                            SubmissionRepository submissionRepository, UserRepository userRepository,
                            TopicRepository topicRepository, TopicService topicService) {
        this.planRepository = planRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.topicService = topicService;
    }

    /** Học viên chỉ thấy plan PUBLISHED; giảng viên / admin thấy mọi plan. */
    @Transactional(readOnly = true)
    public List<PlanSummary> list(Long userId, boolean staff) {
        List<StudyPlan> plans = staff ? planRepository.findAllByOrderByCreatedAtDesc()
                : planRepository.findByStatusOrderByCreatedAtDesc(StudyPlanStatus.PUBLISHED);
        Progress progress = progressOf(userId, staff);
        // ponytail: duyệt items/stages lazy theo từng plan (N+1 theo số plan) — đủ nhanh với vài chục plan.
        return plans.stream().map(p -> {
            PlanDetail d = toDetail(p, progress, Map.of(), Map.of());
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
        return toDetail(plan, progressOf(userId, staff), problems, topicService.refs(topicIds, userId, staff));
    }

    @Transactional
    public PlanDetail create(UpsertPlanRequest request, Long userId) {
        if (planRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Study plan slug already exists: " + request.slug());
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        StudyPlan plan = StudyPlan.builder().slug(request.slug()).createdBy(author).build();
        apply(plan, request, Set.of());
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
        if (plan.getStatus() == StudyPlanStatus.PUBLISHED && request.items().isEmpty()) {
            throw new StateConflictException("A published study plan must keep at least one item");
        }
        Set<Long> current = plan.getItems().stream()
                .flatMap(i -> i.getProblemIds().stream()).collect(Collectors.toSet());
        plan.setSlug(request.slug());
        plan.getItems().clear();
        planRepository.flush(); // xoá stage cũ trước để UNIQUE(problem_id) không chặn chính các đề này
        apply(plan, request, current);
        planRepository.save(plan);
        return get(plan.getSlug(), userId, true);
    }

    @Transactional
    public PlanDetail transition(String slug, StudyPlanStatus target, Long userId, Role role) {
        StudyPlan plan = findOwned(slug, userId, role);
        if (target == StudyPlanStatus.PUBLISHED && plan.getItems().isEmpty()) {
            throw new StateConflictException("A study plan needs at least one item before it can be published");
        }
        plan.setStatus(target);
        return get(plan.getSlug(), userId, true);
    }

    @Transactional
    public void delete(String slug, Long userId, Role role) {
        planRepository.delete(findOwned(slug, userId, role));
    }

    // ------------------------------------------------------------------

    /**
     * @param alreadyInPlan đề vốn có trong plan này: được giữ lại dù đang ARCHIVED (bảo trì stage, spec Q11);
     *                      chỉ đề mới thêm mới bắt buộc PUBLISHED.
     */
    private void apply(StudyPlan plan, UpsertPlanRequest request, Set<Long> alreadyInPlan) {
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
            if (p.getStatus() != ProblemStatus.PUBLISHED && !alreadyInPlan.contains(id)) {
                throw new StateConflictException("Only PUBLISHED problems can be added to a study plan: " + p.getSlug());
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
            if (itemRequest.kind() == StudyPlanItemKind.PROBLEM && itemRequest.problemIds().size() != 1) {
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
                                Map<Long, TopicRef> topics) {
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
                        p != null && p.getStatus() == ProblemStatus.PUBLISHED, status, stageTopics));
            }
            long itemDone = stages.stream().filter(s -> s.status() == StageStatus.DONE).count();
            stageCount += stages.size();
            done += (int) itemDone;
            items.add(new Item(item.getId(), item.getSortOrder(), item.getKind(), item.getTitle(),
                    item.getDescription(), !stages.isEmpty() && itemDone == stages.size(), stages));
        }
        return new PlanDetail(plan.getId(), plan.getSlug(), plan.getTitle(), plan.getDescription(),
                plan.getStatus(), stageCount, done, items,
                plan.getCreatedBy().getId(), plan.getCreatedBy().getUsername());
    }
}
