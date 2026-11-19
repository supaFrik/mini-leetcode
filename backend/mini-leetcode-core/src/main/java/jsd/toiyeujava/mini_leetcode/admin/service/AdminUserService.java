package jsd.toiyeujava.mini_leetcode.admin.service;

import jakarta.persistence.EntityManager;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.ForbiddenOperationException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Trang quản trị: số liệu tổng quan và quản lý người dùng. Chỉ ADMIN (SecurityConfig). */
@Service
public class AdminUserService {

    public static final int PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public AdminUserService(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    public record UserRow(Long id, String username, String email, Role role, UserStatus status, LocalDateTime createdAt) {
        static UserRow of(User u) {
            return new UserRow(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt());
        }
    }

    public record UserList(List<UserRow> items, long total, int page, int size) {
    }

    public record RecentSubmission(Long id, String username, String problemTitle, String problemSlug,
                                   String status, String verdict, LocalDateTime createdAt) {
    }

    public record Stats(long users, long problems, long submissions, long contests,
                        List<RecentSubmission> recentSubmissions, List<UserRow> recentUsers) {
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Stats stats() {
        // Bỏ lần Run (is_quick_run): chỉ tính bài nộp thật, giống bảng xếp hạng.
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select s.id, u.username, p.title, p.slug, s.status, s.verdict, s.created_at"
                                + " from submissions s join users u on u.id = s.user_id join problems p on p.id = s.problem_id"
                                + " where s.is_quick_run = false order by s.id desc")
                .setMaxResults(10)
                .getResultList();
        List<RecentSubmission> recent = rows.stream().map(r -> new RecentSubmission(
                ((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                (String) r[4], (String) r[5], toDateTime(r[6]))).toList();
        List<UserRow> newUsers = userRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id")))
                .map(UserRow::of).getContent();
        return new Stats(userRepository.count(), count("problems"), count("submissions where is_quick_run = false"),
                count("contests"), recent, newUsers);
    }

    @Transactional(readOnly = true)
    public UserList list(String q, int page) {
        String term = q == null ? "" : q.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> result = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term, pageable);
        return new UserList(result.map(UserRow::of).getContent(), result.getTotalElements(), pageable.getPageNumber(), PAGE_SIZE);
    }

    /** Đổi role và/hoặc trạng thái. Không cho tự khoá hay tự hạ role, nên luôn còn ít nhất một ADMIN hoạt động. */
    @Transactional
    public UserRow update(Long actorId, Long userId, Role role, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getId().equals(actorId)) {
            if (status != null && status != user.getStatus()) {
                throw new ForbiddenOperationException("You cannot change the status of your own account");
            }
            if (role != null && role != user.getRole()) {
                throw new ForbiddenOperationException("You cannot change your own role");
            }
        }
        if (role != null) user.setRole(role);
        if (status != null) user.setStatus(status);
        return UserRow.of(userRepository.saveAndFlush(user));
    }

    private long count(String from) {
        return ((Number) entityManager.createNativeQuery("select count(*) from " + from).getSingleResult()).longValue();
    }

    private static LocalDateTime toDateTime(Object v) {
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return (LocalDateTime) v;
    }
}
