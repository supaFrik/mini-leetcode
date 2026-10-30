package jsd.toiyeujava.mini_leetcode.problem.repository;

import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Truy vấn trực tiếp bảng {@code problem_tags}.
 *
 * Bảng nối này không map thành entity riêng (chỉ dùng như
 * {@code @ElementCollection} trong {@code Problem}), nên cần một repository
 * riêng để lấy danh sách chủ đề đang có trong catalogue.
 *
 * Cố tình KHÔNG extends {@code JpaRepository<Problem, Long>} — repository này
 * chỉ đọc, không được phép lỡ tay ghi/xoá Problem.
 */
@org.springframework.stereotype.Repository
public interface ProblemTagRepository extends Repository<Problem, Long> {

    /** Danh sách chủ đề đang được dùng, kèm số bài có chủ đề đó. */
    @Query(value = "select pt.tag, count(pt.problem_id) from problem_tags pt "
            + "join problems p on p.id = pt.problem_id "
            + "where p.status = 'PUBLISHED' "
            + "group by pt.tag order by count(pt.problem_id) desc, pt.tag asc", nativeQuery = true)
    List<Object[]> findTagCounts();
}
