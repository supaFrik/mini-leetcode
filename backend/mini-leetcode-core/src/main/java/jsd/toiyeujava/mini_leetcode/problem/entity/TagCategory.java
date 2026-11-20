package jsd.toiyeujava.mini_leetcode.problem.entity;

import jakarta.persistence.*;
import lombok.*;

/** Nhóm chủ đề do ADMIN quản lý; {@code tags.category_name} trỏ tới {@link #name}. */
@Entity
@Table(name = "tag_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagCategory {

    /** Nhóm dự phòng cho chủ đề chưa có nhóm; không đổi tên, không xoá được. */
    public static final String OTHER = "Other";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
