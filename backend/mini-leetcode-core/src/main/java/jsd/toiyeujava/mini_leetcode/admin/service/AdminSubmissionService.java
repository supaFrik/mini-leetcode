package jsd.toiyeujava.mini_leetcode.admin.service;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Bảng mọi bài nộp cho giảng viên/ADMIN, lọc theo user, bài và verdict. */
@Service
public class AdminSubmissionService {

    public static final int PAGE_SIZE = 50;
    /** Giá trị lọc verdict đặc biệt: bài chưa có verdict (đang chấm hoặc lỗi hệ thống). */
    public static final String PENDING = "PENDING";

    private final SubmissionRepository repository;

    public AdminSubmissionService(SubmissionRepository repository) {
        this.repository = repository;
    }

    public record SubmissionRow(Long id, String username, String problemTitle, String problemSlug, Long contestId,
                                SubmissionLanguage language, SubmissionStatus status, SubmissionVerdict verdict,
                                Double score, Integer executionTimeMs, LocalDateTime createdAt) {
        static SubmissionRow of(Submission s) {
            return new SubmissionRow(s.getId(), s.getUser().getUsername(), s.getProblem().getTitle(), s.getProblem().getSlug(),
                    s.getContestId(), s.getLanguage(), s.getStatus(), s.getVerdict(), s.getScore(), s.getExecutionTimeMs(),
                    s.getCreatedAt());
        }
    }

    public record SubmissionList(List<SubmissionRow> items, long total, int page, int size) {
    }

    /**
     * @param verdict tên {@link SubmissionVerdict}, {@link #PENDING}, hoặc rỗng; giá trị lạ trả lỗi 400.
     * @param ownerId khác null: chỉ bài nộp vào đề do người này tạo (giảng viên chỉ thấy đề của mình).
     */
    @Transactional(readOnly = true)
    public SubmissionList list(String user, String problem, String verdict, int page, Long ownerId) {
        return list(user, problem, verdict, page, PAGE_SIZE, ownerId);
    }

    @Transactional(readOnly = true)
    public SubmissionList list(String user, String problem, String verdict, int page, int size, Long ownerId) {
        String v = verdict == null ? "" : verdict.trim().toUpperCase();
        boolean pending = PENDING.equals(v);
        SubmissionVerdict exact = v.isEmpty() || pending ? null : SubmissionVerdict.valueOf(v);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Submission> result = repository.searchForAdmin(trim(user), trim(problem), exact, pending, ownerId, pageable);
        return new SubmissionList(result.map(SubmissionRow::of).getContent(), result.getTotalElements(),
                pageable.getPageNumber(), size);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
