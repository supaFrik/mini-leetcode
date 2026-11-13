package jsd.toiyeujava.mini_leetcode.contest.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.RegistrationMode;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false, length = 20)
    @Builder.Default
    private RegistrationMode registrationMode = RegistrationMode.OPEN;

    /** Khoá lạc quan: hai người cùng sửa một kỳ thi thì người lưu sau nhận 409. */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Trạng thái hiệu lực của kỳ thi, tính theo giờ tại thời điểm hỏi.
     *
     * <ul>
     *   <li>Cột {@code status} = {@code COMPLETED} luôn là COMPLETED (đóng thủ công / seed).</li>
     *   <li>Đã qua {@code endTime} → COMPLETED, để không cho nộp bài sau giờ.</li>
     *   <li>Cột còn {@code SCHEDULED} nhưng đã tới {@code startTime} → RUNNING. Nhờ vậy kỳ thi tạo
     *       trên giao diện admin tự bắt đầu đúng giờ mà không cần job nào cập nhật cột.</li>
     * </ul>
     *
     * <p>Mọi mốc thời gian trong DB được lưu theo UTC (kết nối JDBC dùng {@code serverTimezone=UTC}),
     * nên phép so sánh dùng {@link java.time.ZoneOffset#UTC} thay vì múi giờ mặc định của JVM.
     */
    @Transient
    public ContestStatus resolveStatus() {
        if (status == null) {
            return ContestStatus.SCHEDULED;
        }
        if (status == ContestStatus.COMPLETED) {
            return ContestStatus.COMPLETED;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (endTime != null && !now.isBefore(endTime)) {
            return ContestStatus.COMPLETED;
        }
        if (status == ContestStatus.SCHEDULED && startTime != null && !now.isBefore(startTime)) {
            return ContestStatus.RUNNING;
        }
        return status;
    }
}
