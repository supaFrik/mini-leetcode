package jsd.toiyeujava.mini_leetcode.topic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.entity.TopicProgressStatus;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;
import jsd.toiyeujava.mini_leetcode.exception.DuplicateResourceException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.topic.dto.TopicDtos.*;
import jsd.toiyeujava.mini_leetcode.topic.entity.Topic;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicSkip;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStep;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStepProgress;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicRepository;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicSkipRepository;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicStepProgressRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chủ đề học (specs/topics.spec.md): xem / làm bài / Skip cho học viên, soạn cho INSTRUCTOR / ADMIN.
 * Đáp án ({@link TopicStep#getAnswer()}) chỉ rời server qua API soạn.
 */
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicStepProgressRepository progressRepository;
    private final TopicSkipRepository skipRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;
    private final StepGrader grader;

    public TopicService(TopicRepository topicRepository, TopicStepProgressRepository progressRepository,
                        TopicSkipRepository skipRepository, ProblemRepository problemRepository,
                        SubmissionRepository submissionRepository, UserRepository userRepository,
                        ObjectMapper mapper) {
        this.topicRepository = topicRepository;
        this.progressRepository = progressRepository;
        this.skipRepository = skipRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.grader = new StepGrader(mapper);
    }

    // ── Học viên ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LearnerTopic get(String slug, Long userId, boolean staff) {
        Topic topic = findVisible(slug, staff);
        Learner learner = learnerOf(userId, List.of(topic));
        Map<Long, Problem> problems = problemsOf(topic.getSteps());
        List<LearnerStep> steps = new ArrayList<>();
        for (TopicStep step : topic.getSteps()) {
            TopicStepProgress p = learner.progress().get(step.getId());
            steps.add(new LearnerStep(step.getId(), step.getSortOrder() + 1, step.getType(), step.getTitle(),
                    learnerContent(step, problems), learner.passed(step), p == null ? 0 : p.getAttempts()));
        }
        int done = (int) steps.stream().filter(LearnerStep::passed).count();
        return new LearnerTopic(topic.getId(), topic.getSlug(), topic.getTitle(), topic.getSummary(),
                topic.getEstMinutes(), topic.getStatus(), learner.status(topic, done), steps.size(), done, steps);
    }

    @Transactional
    public AttemptResult attempt(String slug, Long stepId, JsonNode submitted, Long userId, boolean staff) {
        Topic topic = findVisible(slug, staff);
        TopicStep step = topic.getSteps().stream().filter(s -> s.getId().equals(stepId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Step not found in topic " + slug + ": " + stepId));
        StepGrader.Grade grade = grader.grade(step.getType(), read(step.getContent()), read(step.getAnswer()), submitted);

        TopicStepProgress progress = progressRepository.findById(new TopicStepProgress.Key(userId, stepId))
                .orElseGet(() -> TopicStepProgress.builder().userId(userId).stepId(stepId).build());
        progress.setAttempts(progress.getAttempts() + 1);
        progress.setPassed(progress.isPassed() || grade.correct());
        progressRepository.save(progress);

        Learner learner = learnerOf(userId, List.of(topic));
        int done = (int) topic.getSteps().stream().filter(learner::passed).count();
        return new AttemptResult(grade.correct(), grade.message(), grade.explanation(), progress.isPassed(),
                progress.getAttempts(), learner.status(topic, done), done, topic.getSteps().size());
    }

    @Transactional
    public void skip(String slug, Long userId, boolean staff, boolean skipped) {
        Topic topic = findVisible(slug, staff);
        TopicSkip.Key key = new TopicSkip.Key(userId, topic.getId());
        if (skipped) {
            if (!skipRepository.existsById(key)) {
                skipRepository.save(new TopicSkip(userId, topic.getId()));
            }
        } else {
            skipRepository.deleteById(key);
        }
    }

    /**
     * Thông tin + tiến độ của các chủ đề gắn vào stage (dùng cho trang plan). Học viên chỉ thấy chủ đề PUBLISHED;
     * id không còn tồn tại bị bỏ qua.
     */
    @Transactional(readOnly = true)
    public Map<Long, TopicRef> refs(Collection<Long> topicIds, Long userId, boolean staff) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        List<Topic> topics = topicRepository.findAllById(new HashSet<>(topicIds)).stream()
                .filter(t -> staff || t.getStatus() == StudyPlanStatus.PUBLISHED).toList();
        Learner learner = learnerOf(userId, topics);
        Map<Long, TopicRef> refs = new HashMap<>();
        for (Topic t : topics) {
            int done = (int) t.getSteps().stream().filter(learner::passed).count();
            refs.put(t.getId(), new TopicRef(t.getId(), t.getSlug(), t.getTitle(), t.getSummary(), t.getEstMinutes(),
                    learner.status(t, done), t.getSteps().size(), done));
        }
        return refs;
    }

    // ── Soạn ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopicSummary> list(String search) {
        String q = search == null || search.isBlank() ? null : search.trim();
        return topicRepository.search(q).stream().map(t -> new TopicSummary(t.getId(), t.getSlug(), t.getTitle(),
                t.getSummary(), t.getEstMinutes(), t.getStatus(), t.getSteps().size(), t.getCreatedBy().getId(),
                t.getCreatedBy().getUsername(), t.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public AuthoringTopic getForAuthoring(String slug) {
        Topic topic = topicRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + slug));
        Map<Long, Problem> problems = problemsOf(topic.getSteps());
        List<AuthoringStep> steps = topic.getSteps().stream().map(s -> new AuthoringStep(s.getId(), s.getType(),
                s.getTitle(), read(s.getContent()), read(s.getAnswer()), s.getProblemId(),
                s.getProblemId() == null || problems.get(s.getProblemId()) == null ? null
                        : problems.get(s.getProblemId()).getSlug())).toList();
        return new AuthoringTopic(topic.getId(), topic.getSlug(), topic.getTitle(), topic.getSummary(),
                topic.getEstMinutes(), topic.getStatus(), steps, topic.getCreatedBy().getId(),
                topic.getCreatedBy().getUsername());
    }

    @Transactional
    public AuthoringTopic create(UpsertTopicRequest request, Long userId) {
        if (topicRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Topic slug already exists: " + request.slug());
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        Topic topic = Topic.builder().slug(request.slug()).createdBy(author).build();
        apply(topic, request);
        topicRepository.save(topic);
        return getForAuthoring(topic.getSlug());
    }

    /** Thay nội dung; step gửi kèm {@code id} cũ được cập nhật tại chỗ nên tiến độ học viên còn nguyên. */
    @Transactional
    public AuthoringTopic update(String slug, UpsertTopicRequest request, Long userId, Role role) {
        Topic topic = findOwned(slug, userId, role);
        if (!topic.getSlug().equals(request.slug()) && topicRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Topic slug already exists: " + request.slug());
        }
        if (topic.getStatus() == StudyPlanStatus.PUBLISHED && request.steps().isEmpty()) {
            throw new StateConflictException("A published topic must keep at least one step");
        }
        topic.setSlug(request.slug());
        apply(topic, request);
        topicRepository.save(topic);
        return getForAuthoring(topic.getSlug());
    }

    @Transactional
    public AuthoringTopic transition(String slug, StudyPlanStatus target, Long userId, Role role) {
        Topic topic = findOwned(slug, userId, role);
        if (target == StudyPlanStatus.PUBLISHED && topic.getSteps().isEmpty()) {
            throw new StateConflictException("A topic needs at least one step before it can be published");
        }
        topic.setStatus(target);
        return getForAuthoring(topic.getSlug());
    }

    @Transactional
    public void delete(String slug, Long userId, Role role) {
        Topic topic = findOwned(slug, userId, role);
        if (topicRepository.countPlanUsages(topic.getId()) > 0) {
            throw new StateConflictException("Remove this topic from every study plan before deleting it");
        }
        topicRepository.delete(topic);
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    private void apply(Topic topic, UpsertTopicRequest request) {
        topic.setTitle(request.title().trim());
        topic.setSummary(request.summary() == null || request.summary().isBlank() ? null : request.summary().trim());
        topic.setEstMinutes(request.estMinutes());

        Map<Long, TopicStep> existing = topic.getSteps().stream()
                .collect(Collectors.toMap(TopicStep::getId, Function.identity()));
        List<TopicStep> next = new ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            StepRequest r = request.steps().get(i);
            String where = "steps[" + i + "] (" + r.title() + ")";
            grader.validate(r.type(), r.content(), r.answer(), where);
            Long problemId = null;
            if (r.type() == TopicStepType.CODE) {
                Problem problem = r.problemId() == null ? null : problemRepository.findById(r.problemId()).orElse(null);
                if (problem == null) {
                    throw new IllegalArgumentException(where + ": choose an existing problem for the code step");
                }
                problemId = problem.getId();
            }
            TopicStep step = r.id() != null && existing.containsKey(r.id()) && existing.get(r.id()).getType() == r.type()
                    ? existing.remove(r.id()) : TopicStep.builder().topic(topic).build();
            step.setSortOrder(i);
            step.setType(r.type());
            step.setTitle(r.title().trim());
            step.setContent(write(r.content()));
            step.setAnswer(r.type() == TopicStepType.THEORY || r.type() == TopicStepType.CODE ? null : write(r.answer()));
            step.setProblemId(problemId);
            next.add(step);
        }
        topic.getSteps().clear();
        topic.getSteps().addAll(next);
    }

    /** Nội dung gửi học viên: bỏ đáp án, xáo trộn; CODE thêm thông tin đề. */
    private JsonNode learnerContent(TopicStep step, Map<Long, Problem> problems) {
        JsonNode content = read(step.getContent());
        if (step.getType() == TopicStepType.CODE) {
            ObjectNode copy = content.deepCopy();
            Problem problem = problems.get(step.getProblemId());
            if (problem != null) {
                copy.put("problemId", problem.getId());
                copy.put("problemSlug", problem.getSlug());
                copy.put("problemTitle", problem.getTitle());
                copy.put("difficulty", problem.getDifficulty() == null ? null : problem.getDifficulty().name());
                copy.put("published", problem.getStatus() == ProblemStatus.PUBLISHED);
            }
            return copy;
        }
        return grader.publicContent(step.getType(), content, read(step.getAnswer()), step.getId());
    }

    private Map<Long, Problem> problemsOf(List<TopicStep> steps) {
        List<Long> ids = steps.stream().map(TopicStep::getProblemId).filter(java.util.Objects::nonNull).toList();
        return ids.isEmpty() ? Map.of() : problemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
    }

    private record Learner(Map<Long, TopicStepProgress> progress, Set<Long> solvedProblems, Set<Long> skipped) {
        boolean passed(TopicStep step) {
            if (step.getType() == TopicStepType.CODE) {
                return step.getProblemId() != null && solvedProblems.contains(step.getProblemId());
            }
            TopicStepProgress p = progress.get(step.getId());
            return p != null && p.isPassed();
        }

        TopicProgressStatus status(Topic topic, int done) {
            if (!topic.getSteps().isEmpty() && done == topic.getSteps().size()) {
                return TopicProgressStatus.DONE;
            }
            if (skipped.contains(topic.getId())) {
                return TopicProgressStatus.SKIPPED;
            }
            boolean touched = done > 0 || topic.getSteps().stream()
                    .anyMatch(s -> progress.containsKey(s.getId()) && progress.get(s.getId()).getAttempts() > 0);
            return touched ? TopicProgressStatus.IN_PROGRESS : TopicProgressStatus.NEW;
        }
    }

    private Learner learnerOf(Long userId, List<Topic> topics) {
        List<Long> stepIds = topics.stream().flatMap(t -> t.getSteps().stream()).map(TopicStep::getId).toList();
        Map<Long, TopicStepProgress> progress = stepIds.isEmpty() ? Map.of()
                : progressRepository.findByUserIdAndStepIdIn(userId, stepIds).stream()
                .collect(Collectors.toMap(TopicStepProgress::getStepId, Function.identity()));
        boolean hasCode = topics.stream().flatMap(t -> t.getSteps().stream())
                .anyMatch(s -> s.getType() == TopicStepType.CODE);
        Set<Long> solved = hasCode
                ? new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(userId, SubmissionVerdict.AC)) : Set.of();
        Set<Long> skipped = topics.isEmpty() ? Set.of()
                : skipRepository.findByUserIdAndTopicIdIn(userId, topics.stream().map(Topic::getId).toList()).stream()
                .map(TopicSkip::getTopicId).collect(Collectors.toSet());
        return new Learner(progress, solved, skipped);
    }

    private Topic findVisible(String slug, boolean staff) {
        return topicRepository.findBySlug(slug)
                .filter(t -> staff || t.getStatus() == StudyPlanStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + slug));
    }

    private Topic findOwned(String slug, Long userId, Role role) {
        Topic topic = topicRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + slug));
        if (role != Role.ADMIN && !topic.getCreatedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Only the author or an admin can modify this topic");
        }
        return topic;
    }

    private JsonNode read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored topic JSON is invalid", e);
        }
    }

    private String write(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
