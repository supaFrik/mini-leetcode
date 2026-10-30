package jsd.toiyeujava.mini_leetcode.contest.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Bài toán thuộc một kỳ thi. Nhãn (A, B, C...) là thứ tự hiển thị trên UI.
 */
@Entity
@Table(
        name = "contest_problems",
        uniqueConstraints = @UniqueConstraint(name = "uq_contest_problem", columnNames = {"contest_id", "problem_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, length = 10)
    private String label;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
