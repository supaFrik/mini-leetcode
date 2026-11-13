package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Loại bài toán: Thuật toán tiêu chuẩn hoặc OOP Grinding.
 */
public enum ProblemType {
    ALGORITHMIC,
    OOP_GRINDING;

    public boolean isOopGrinding() {
        return this == OOP_GRINDING;
    }
}
