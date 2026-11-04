package jsd.toiyeujava.mini_leetcode.submission.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "contest_id")
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionLanguage language;

    @Column(name = "code_storage_uri", nullable = false, length = 500)
    private String codeStorageUri;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SubmissionVerdict verdict;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @Column(name = "pass_test_cases", nullable = false)
    @Builder.Default
    private Integer passTestCases = 0;

    @Column(name = "total_test_cases", nullable = false)
    @Builder.Default
    private Integer totalTestCases = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "score")
    private Double score;

    @Column(name = "execution_phase", length = 30)
    private String executionPhase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "json")
    private String scoreBreakdown;

    /** Run (chạy thử) chứ không phải Submit: không tính vào thống kê, bài đã giải, bảng xếp hạng. */
    @Column(name = "is_quick_run", nullable = false)
    @Builder.Default
    private boolean quickRun = false;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
