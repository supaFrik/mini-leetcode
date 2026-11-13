package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Trạng thái của một stage đối với học viên hiện tại (suy ra từ submissions).
 */
public enum StageStatus {
    LOCKED,
    NEVER,
    IN_PROGRESS,
    DONE;

    public boolean isCompleted() {
        return this == DONE;
    }

    public boolean isLocked() {
        return this == LOCKED;
    }

    public boolean canAttempt() {
        return this != LOCKED;
    }
}
