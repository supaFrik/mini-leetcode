package jsd.toiyeujava.mini_leetcode.studyplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanEntryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một mục trong session: LESSON có {@code topicId}, PROBLEM / STAGE có {@code problemId}. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanEntry {

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyPlanEntryType type;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "problem_id")
    private Long problemId;
}
