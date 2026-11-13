package jsd.toiyeujava.mini_leetcode.studyplan.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanItemKind;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Một mục trong lộ trình. Stage = một {@code Problem}; vị trí trong {@link #problemIds}
 * chính là {@code stage_no} (0-based), giống cách {@code problem_tags} lưu thứ tự.
 */
@Entity
@Table(name = "study_plan_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private StudyPlan plan;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyPlanItemKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_plan_item_stages", joinColumns = @JoinColumn(name = "item_id"))
    @OrderColumn(name = "stage_no")
    @Column(name = "problem_id", nullable = false)
    @Builder.Default
    private List<Long> problemIds = new ArrayList<>();

    /** Chủ đề học gắn vào từng stage, theo thứ tự hiển thị (specs/topics.spec.md). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_plan_stage_topics", joinColumns = @JoinColumn(name = "item_id"))
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<StageTopic> stageTopics = new ArrayList<>();
}
