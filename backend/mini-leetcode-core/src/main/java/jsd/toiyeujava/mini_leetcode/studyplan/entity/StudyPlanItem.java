package jsd.toiyeujava.mini_leetcode.studyplan.entity;

import jakarta.persistence.*;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanItemKind;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Một session của lộ trình: danh sách mục (lesson / problem / project stage) có thứ tự.
 * Stage N trong URL học viên = mục problem/stage thứ N của session.
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
    @Builder.Default
    private StudyPlanItemKind kind = StudyPlanItemKind.SESSION;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** sort_order có thể có khoảng trống (dữ liệu chuyển từ V25) nên dùng @OrderBy, không dùng @OrderColumn. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_plan_entries", joinColumns = @JoinColumn(name = "session_id"))
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<StudyPlanEntry> entries = new ArrayList<>();

    /** Đề của session (problem + stage) theo thứ tự. */
    public List<Long> getProblemIds() {
        return entries.stream().filter(e -> e.getProblemId() != null).map(StudyPlanEntry::getProblemId).toList();
    }

    public List<Long> getTopicIds() {
        return entries.stream().filter(e -> e.getTopicId() != null).map(StudyPlanEntry::getTopicId).toList();
    }
}
