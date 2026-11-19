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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

    public record DayCount(LocalDate date, long total, long accepted) {
    }

    public record ProblemRate(String slug, String title, long submissions, double acRate) {
    }

    public record UpcomingContest(Long id, String title, LocalDateTime startTime) {
    }

    public record Stats(long users, long problems, long submissions, long contests,
                        List<RecentSubmission> recentSubmissions, List<UserRow> recentUsers,
                        List<DayCount> daily, double acRate, long activeUsers7d, Map<String, Long> byDifficulty,
                        List<ProblemRate> lowestAc, List<UpcomingContest> upcomingContests) {
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
        LocalDate today = LocalDate.now();
        return new Stats(userRepository.count(), count("problems"), count("submissions where is_quick_run = false"),
                count("contests"), recent, newUsers, daily(today), acRate(),
                ((Number) entityManager.createNativeQuery("select count(distinct user_id) from submissions"
                                + " where is_quick_run = false and created_at >= :since")
                        .setParameter("since", today.minusDays(6).atStartOfDay()).getSingleResult()).longValue(),
                byDifficulty(), lowestAc(), upcoming());
    }

    /** 14 ngày gần nhất (kể cả hôm nay), ngày không có bài nộp vẫn có mặt với số 0. */
    @SuppressWarnings("unchecked")
    private List<DayCount> daily(LocalDate today) {
        LocalDate from = today.minusDays(13);
        Map<LocalDate, long[]> byDay = new HashMap<>();
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select cast(created_at as date), count(*), sum(case when verdict = 'AC' then 1 else 0 end)"
                                + " from submissions where is_quick_run = false and created_at >= :from"
                                + " group by cast(created_at as date)")
                .setParameter("from", from.atStartOfDay()).getResultList();
        for (Object[] r : rows) {
            LocalDate d = r[0] instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) r[0];
            byDay.put(d, new long[]{((Number) r[1]).longValue(), r[2] == null ? 0 : ((Number) r[2]).longValue()});
        }
        List<DayCount> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            long[] v = byDay.getOrDefault(d, new long[2]);
            out.add(new DayCount(d, v[0], v[1]));
        }
        return out;
    }

    /** Tỉ lệ AC trên các bài đã chấm xong (0..1). */
    private double acRate() {
        Object[] r = (Object[]) entityManager.createNativeQuery(
                "select count(*), sum(case when verdict = 'AC' then 1 else 0 end) from submissions"
                        + " where is_quick_run = false and verdict is not null").getSingleResult();
        long total = ((Number) r[0]).longValue();
        return total == 0 ? 0 : (r[1] == null ? 0 : ((Number) r[1]).doubleValue()) / total;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> byDifficulty() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String d : List.of("EASY", "MEDIUM", "HARD")) out.put(d, 0L);
        List<Object[]> rows = entityManager.createNativeQuery("select difficulty, count(*) from problems group by difficulty")
                .getResultList();
        for (Object[] r : rows) out.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return out;
    }

    /** 5 bài có tỉ lệ AC thấp nhất, chỉ tính bài đã có ít nhất 1 lần chấm. */
    @SuppressWarnings("unchecked")
    private List<ProblemRate> lowestAc() {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select p.slug, p.title, count(*) n, sum(case when s.verdict = 'AC' then 1 else 0 end) ac"
                                + " from submissions s join problems p on p.id = s.problem_id"
                                + " where s.is_quick_run = false and s.verdict is not null"
                                + " group by p.id, p.slug, p.title"
                                + " order by sum(case when s.verdict = 'AC' then 1 else 0 end) * 1.0 / count(*), count(*) desc")
                .setMaxResults(5).getResultList();
        return rows.stream().map(r -> {
            long n = ((Number) r[2]).longValue();
            return new ProblemRate((String) r[0], (String) r[1], n, n == 0 ? 0 : ((Number) r[3]).doubleValue() / n);
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<UpcomingContest> upcoming() {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select id, title, start_time from contests where status = 'SCHEDULED' and start_time > :now order by start_time")
                .setParameter("now", LocalDateTime.now()).setMaxResults(5).getResultList();
        return rows.stream().map(r -> new UpcomingContest(((Number) r[0]).longValue(), (String) r[1], toDateTime(r[2]))).toList();
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
