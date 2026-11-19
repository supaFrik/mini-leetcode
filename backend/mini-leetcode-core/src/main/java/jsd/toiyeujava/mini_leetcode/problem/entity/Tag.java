package jsd.toiyeujava.mini_leetcode.problem.entity;

import jakarta.persistence.*;
import lombok.*;

/** Nhóm của một chủ đề (bảng {@code tags}, V15). Bài vẫn lưu tên chủ đề trong {@code problem_tags}. */
@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    public enum Category { DSA, JAVA_CORE, SPRING_BOOT, OOP, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Category category = Category.OTHER;
}
