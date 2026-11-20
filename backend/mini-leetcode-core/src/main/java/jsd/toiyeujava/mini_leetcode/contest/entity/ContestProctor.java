package jsd.toiyeujava.mini_leetcode.contest.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import lombok.*;

/** Giám thị của một kỳ thi: xem live view và báo cáo vi phạm, không sửa kỳ thi. */
@Entity
@Table(
        name = "contest_proctors",
        uniqueConstraints = @UniqueConstraint(name = "uq_contest_proctor", columnNames = {"contest_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestProctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
