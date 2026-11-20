package jsd.toiyeujava.mini_leetcode.ranking.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Bảng xếp hạng toàn site: số bài khác nhau đã AC, hoà thì ít lần nộp hơn đứng trên.
 * Chỉ tính học viên (role USER, ACTIVE), bỏ lần Run (is_quick_run). Không nhân hệ số độ khó.
 */
@Service
public class RankingService {

    public static final int LIMIT = 100;

    private final EntityManager entityManager;

    public RankingService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record RankingRow(int rank, Long userId, String username, int solved, int submissions) {
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<RankingRow> top() {
        // shortcut: aggregates the whole submissions table per request; cache or materialize when it grows large.
        List<Object[]> rows = entityManager.createNativeQuery(
                "select u.id, u.username,"
                        + " count(distinct case when s.verdict = 'AC' then s.problem_id end) as solved,"
                        + " count(s.id) as attempts"
                        + " from users u join submissions s on s.user_id = u.id"
                        + " where s.is_quick_run = false and u.role = 'USER' and u.status = 'ACTIVE'"
                        + " group by u.id, u.username"
                        + " having count(distinct case when s.verdict = 'AC' then s.problem_id end) > 0"
                        + " order by solved desc, attempts asc, u.username asc")
                .setMaxResults(LIMIT)
                .getResultList();
        List<RankingRow> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            int solved = ((Number) r[2]).intValue();
            int attempts = ((Number) r[3]).intValue();
            RankingRow prev = result.isEmpty() ? null : result.get(result.size() - 1);
            // Cùng số bài và cùng số lần nộp thì cùng hạng.
            int rank = prev != null && prev.solved() == solved && prev.submissions() == attempts ? prev.rank() : i + 1;
            result.add(new RankingRow(rank, ((Number) r[0]).longValue(), String.valueOf(r[1]), solved, attempts));
        }
        return result;
    }
}
