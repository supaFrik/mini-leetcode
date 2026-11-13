package jsd.toiyeujava.mini_leetcode.topic.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;
import lombok.*;

/**
 * Một step của chủ đề. {@code content} là JSON công khai; {@code answer} là JSON đáp án và
 * KHÔNG BAO GIỜ được trả cho học viên. {@code problemId} chỉ dùng cho {@link TopicStepType#CODE}.
 */
@Entity
@Table(name = "topic_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopicStepType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "problem_id")
    private Long problemId;
}
