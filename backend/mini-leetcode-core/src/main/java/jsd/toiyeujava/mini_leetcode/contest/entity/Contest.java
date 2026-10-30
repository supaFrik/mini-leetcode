package jsd.toiyeujava.mini_leetcode.contest.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "contests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_policy", nullable = false, length = 20)
    @Builder.Default
    private ScoringPolicy scoringPolicy = ScoringPolicy.ACM_ICPC;

    @Column(name = "max_violations_allowed", nullable = false)
    @Builder.Default
    private Integer maxViolationsAllowed = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContestStatus status = ContestStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Trạng thái hiệu lực của kỳ thi.
     *
     * <p>Cột {@code status} trong DB là nguồn chính (được quản lý bởi giảng viên / seed data).
     * Ngoại lệ duy nhất: kỳ thi đang {@code RUNNING} nhưng đã qua {@code endTime} sẽ được coi là
     * {@code COMPLETED} để không cho nộp bài sau khi kỳ thi đã kết thúc.
     *
     * <p>Mọi mốc thời gian trong DB được lưu theo UTC (kết nối JDBC dùng {@code serverTimezone=UTC}),
     * nên phép so sánh dùng {@link java.time.ZoneOffset#UTC} thay vì múi giờ mặc định của JVM.
     * Nhờ vậy kết luận "kỳ thi đã kết thúc" luôn nhất quán với những gì client nhìn thấy.
     */
    @Transient
    public ContestStatus resolveStatus() {
        if (status == null) {
            return ContestStatus.SCHEDULED;
        }
        if (status == ContestStatus.RUNNING && endTime != null
                && !LocalDateTime.now(ZoneOffset.UTC).isBefore(endTime)) {
            return ContestStatus.COMPLETED;
        }
        return status;
    }
}
