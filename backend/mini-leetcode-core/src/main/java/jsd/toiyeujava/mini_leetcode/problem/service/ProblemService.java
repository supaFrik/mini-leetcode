package jsd.toiyeujava.mini_leetcode.problem.service;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.entity.UserProgress;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemTagRepository;
import jsd.toiyeujava.mini_leetcode.problem.util.TagNormalizer;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProblemService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-+");

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;
    private final ProblemTagRepository problemTagRepository;
    private final SubmissionRepository submissionRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this(problemRepository, null, null, null, null);
    }

    @Autowired
    public ProblemService(
            ProblemRepository problemRepository,
            TestCaseRepository testCaseRepository,
            StorageService storageService,
            ProblemTagRepository problemTagRepository,
            SubmissionRepository submissionRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.storageService = storageService;
        this.problemTagRepository = problemTagRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request, User creator) {
        String slug = generateUniqueSlug(request.title());

        Problem problem = Problem.builder()
                .title(request.title())
                .slug(slug)
                .description(request.description())
                .difficulty(request.difficulty())
                .timeLimitMs(request.getTimeLimitMsOrDefault())
                .memoryLimitMb(request.getMemoryLimitMbOrDefault())
                .status(ProblemStatus.DRAFT)
                .tags(TagNormalizer.normalize(request.tags()))
                .createdBy(creator)
                .build();

        problem = problemRepository.save(problem);
        return ProblemResponse.fromEntity(problem);
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, UpdateProblemRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Problem title cannot be blank");
            }
            if (!request.title().trim().equals(problem.getTitle())) {
                problem.setTitle(request.title().trim());
                problem.setSlug(generateUniqueSlug(request.title().trim()));
            }
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new IllegalArgumentException("Problem description cannot be blank");
            }
            problem.setDescription(request.description());
        }
        if (request.difficulty() != null) {
            problem.setDifficulty(request.difficulty());
        }
        if (request.timeLimitMs() != null) {
            problem.setTimeLimitMs(request.timeLimitMs());
        }
        if (request.memoryLimitMb() != null) {
            problem.setMemoryLimitMb(request.memoryLimitMb());
        }
        if (request.tags() != null) {
            problem.setTags(TagNormalizer.normalize(request.tags()));
        }

        problem = problemRepository.save(problem);
        return ProblemResponse.fromEntity(problem);
    }

    /**
     * Chuyển trạng thái vòng đời của bài.
     *
     * <p>Toàn bộ luật nằm ở server: client chỉ gửi trạng thái đích.
     * Chuyển sang {@code PUBLISHED} đòi hỏi bài đã có ít nhất một testcase
     * công khai — nếu không thì bài sẽ không có ví dụ nào cho thí sinh.
     */
    @Transactional
    public ProblemResponse transitionStatus(Long id, ProblemStatus target) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        ProblemStatus current = problem.getStatus();

        if (current == target) {
            throw new StateConflictException(
                    "Problem is already in status " + target);
        }

        if (!ProblemLifecycle.isAllowed(current, target)) {
            throw new StateConflictException("Cannot transition problem from " + current
                    + " to " + target + ". Allowed targets: "
                    + ProblemLifecycle.allowedTargets(current));
        }

        if (target == ProblemStatus.PUBLISHED && !hasPublicTestCase(id)) {
            throw new StateConflictException(
                    "Cannot publish problem " + id + ": at least one public test case is required");
        }

        problem.setStatus(target);
        problem = problemRepository.save(problem);
        return ProblemResponse.fromEntity(problem);
    }

    /** Bài có ít nhất một testcase công khai (is_hidden = false) không? */
    private boolean hasPublicTestCase(Long problemId) {
        if (testCaseRepository == null) {
            return false;
        }
        return testCaseRepository.countByProblemIdAndIsHiddenFalse(problemId) > 0;
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemById(Long id) {
        return getProblemById(id, null);
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemById(Long id, Long userId) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));
        return ProblemResponse.fromEntity(problem, resolveProgress(userId, List.of(problem), solvedIds(userId), attemptedIds(userId)).get(id));
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemBySlug(String slug) {
        return getProblemBySlug(slug, null);
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemBySlug(String slug, Long userId) {
        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with slug: " + slug));
        return ProblemResponse.fromEntity(problem, resolveProgress(userId, List.of(problem), solvedIds(userId), attemptedIds(userId)).get(problem.getId()));
    }

    private Set<Long> solvedIds(Long userId) {
        if (userId == null || submissionRepository == null) return Set.of();
        return new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(userId, SubmissionVerdict.AC));
    }

    private Set<Long> attemptedIds(Long userId) {
        if (userId == null || submissionRepository == null) return Set.of();
        return new HashSet<>(submissionRepository.findAttemptedProblemIdsByUserId(userId));
    }

    @Transactional(readOnly = true)
    public Page<ProblemResponse> getProblems(Difficulty difficulty, ProblemStatus status, String search,                                             List<String> tags, UserProgress progress, Long userId, Pageable pageable) {
        // Bộ lọc tiến độ phụ thuộc vào user, nên phải tính TRƯỚC khi phân trang —
        // lọc sau khi đã cắt trang sẽ cho kết quả sai (trang thiếu hoặc sai số lượng).
        Set<Long> solvedIds = Set.of();
        Set<Long> attemptedIds = Set.of();
        if (userId != null && submissionRepository != null) {
            solvedIds = new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(userId, SubmissionVerdict.AC));
            attemptedIds = new HashSet<>(submissionRepository.findAttemptedProblemIdsByUserId(userId));
        }
        final Set<Long> solved = solvedIds;
        final Set<Long> attempted = attemptedIds;

        Specification<Problem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.toLowerCase(Locale.ROOT).trim() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchPattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }
            if (tags != null && !tags.isEmpty()) {
                // OR giữa các chủ đề, so khớp chính xác không phân biệt hoa/thường:
                // bài chỉ cần thuộc ÍT NHẤT MỘT chủ đề được chọn.
                // Chuẩn hoá trước: trim, bỏ rỗng, khử trùng lặp, giữ display casing.
                List<String> normalized = TagNormalizer.normalize(tags);
                if (!normalized.isEmpty()) {
                    Join<Problem, String> tagJoin = root.join("tags", JoinType.INNER);
                    List<Predicate> tagPredicates = new ArrayList<>();
                    for (String tag : normalized) {
                        tagPredicates.add(cb.equal(cb.lower(tagJoin), tag.toLowerCase(Locale.ROOT)));
                    }
                    predicates.add(cb.or(tagPredicates.toArray(new Predicate[0])));
                    query.distinct(true);
                }
            }
            if (progress != null && userId != null) {
                switch (progress) {
                    case DONE -> predicates.add(root.get("id").in(solved));
                    case IN_PROGRESS -> predicates.add(cb.and(
                            root.get("id").in(attempted),
                            cb.not(root.get("id").in(solved))));
                    case NEVER -> predicates.add(cb.not(root.get("id").in(attempted)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Problem> page = problemRepository.findAll(spec, pageable);
        Map<Long, UserProgress> progressByProblem = resolveProgress(userId, page.getContent(), solvedIds, attemptedIds);

        return page.map(p -> ProblemResponse.fromEntity(p, progressByProblem.get(p.getId())));
    }

    /**
     * Tiến độ của user với từng bài trong trang: có bài nộp AC → DONE,
     * có bài nộp (chưa AC) → IN_PROGRESS, chưa nộp gì → NEVER.
     */
    private Map<Long, UserProgress> resolveProgress(Long userId, List<Problem> problems,
                                                     Set<Long> solvedIds, Set<Long> attemptedIds) {
        if (userId == null || problems.isEmpty() || submissionRepository == null) {
            return Map.of();
        }

        Map<Long, UserProgress> map = new HashMap<>();
        for (Problem p : problems) {
            Long id = p.getId();
            if (solvedIds.contains(id)) {
                map.put(id, UserProgress.DONE);
            } else if (attemptedIds.contains(id)) {
                map.put(id, UserProgress.IN_PROGRESS);
            } else {
                map.put(id, UserProgress.NEVER);
            }
        }
        return map;
    }

    /** Danh sách chủ đề đang có trong catalogue, kèm số bài tương ứng. */
    @Transactional(readOnly = true)
    public List<TagCount> getTagCounts() {
        if (problemTagRepository == null) {
            return List.of();
        }
        return problemTagRepository.findTagCounts().stream()
                .map(row -> new TagCount(String.valueOf(row[0]), ((Number) row[1]).intValue()))
                .toList();
    }

    /** Số bài tập thuộc một chủ đề. */
    public record TagCount(String tag, int count) {
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (testCaseRepository != null && storageService != null) {
            List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(id);
            for (TestCase tc : testCases) {
                storageService.deleteFileByUri(tc.getInputFileUri());
                storageService.deleteFileByUri(tc.getExpectedOutputUri());
            }
            testCaseRepository.deleteAll(testCases);
        }

        problemRepository.delete(problem);
    }

    public String generateUniqueSlug(String title) {
        String baseSlug = toSlug(title);
        if (!problemRepository.existsBySlug(baseSlug)) {
            return baseSlug;
        }

        int count = 1;
        while (true) {
            String candidate = baseSlug + "-" + count;
            if (!problemRepository.existsBySlug(candidate)) {
                return candidate;
            }
            count++;
        }
    }

    public static String toSlug(String input) {
        if (!StringUtils.hasText(input)) {
            return "problem";
        }
        String replaced = input.trim().replace('đ', 'd').replace('Đ', 'D');
        String nowhitespace = WHITESPACE.matcher(replaced).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = MULTI_HYPHEN.matcher(slug).replaceAll("-").toLowerCase(Locale.ENGLISH);
        if (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isEmpty() ? "problem" : slug;
    }
}
