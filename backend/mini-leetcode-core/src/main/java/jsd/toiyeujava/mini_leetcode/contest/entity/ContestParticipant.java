package jsd.toiyeujava.mini_leetcode.contest.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "contest_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_contest_user", columnNames = {"contest_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.REGISTERED;

    @Column(name = "violation_count", nullable = false)
    @Builder.Default
    private Integer violationCount = 0;

    @Column(name = "last_away_start_time")
    private LocalDateTime lastAwayStartTime;

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(name = "penalty_time_minutes", nullable = false)
    @Builder.Default
    private Integer penaltyTimeMinutes = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
