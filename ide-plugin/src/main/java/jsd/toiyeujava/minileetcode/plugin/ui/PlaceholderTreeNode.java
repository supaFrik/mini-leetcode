package jsd.toiyeujava.minileetcode.plugin.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Objects;

/**
 * Node trạng thái đặc biệt hiển thị thông báo tạm thời trên Swing Tree:
 * - LOADING: Đang tải danh sách lộ trình từ server ("Loading study plans...")
 * - EMPTY: Không có lộ trình nào ("No study plans available")
 * - ERROR: Lỗi kết nối hoặc xác thực ("Failed to load study plans: ...")
 */
public class PlaceholderTreeNode extends DefaultMutableTreeNode {

    public enum PlaceholderType {
        LOADING,
        EMPTY,
        ERROR
    }

    private final PlaceholderType type;
    private final String message;

    public PlaceholderTreeNode(@NotNull PlaceholderType type, @NotNull String message) {
        super(Objects.requireNonNull(message, "message must not be null"), false);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.message = message;
    }

    public static PlaceholderTreeNode loading() {
        return new PlaceholderTreeNode(PlaceholderType.LOADING, "Loading study plans...");
    }

    public static PlaceholderTreeNode loading(@Nullable String message) {
        return new PlaceholderTreeNode(
                PlaceholderType.LOADING,
                (message != null && !message.isBlank()) ? message : "Loading study plans..."
        );
    }

    public static PlaceholderTreeNode empty() {
        return new PlaceholderTreeNode(PlaceholderType.EMPTY, "No study plans available");
    }

    public static PlaceholderTreeNode empty(@Nullable String message) {
        return new PlaceholderTreeNode(
                PlaceholderType.EMPTY,
                (message != null && !message.isBlank()) ? message : "No study plans available"
        );
    }

    public static PlaceholderTreeNode error(@NotNull String errorMessage) {
        Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        String formatted = errorMessage.startsWith("Failed to load study plans:")
                ? errorMessage
                : "Failed to load study plans: " + errorMessage;
        return new PlaceholderTreeNode(PlaceholderType.ERROR, formatted);
    }

    public PlaceholderType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isLoading() {
        return type == PlaceholderType.LOADING;
    }

    public boolean isEmptyState() {
        return type == PlaceholderType.EMPTY;
    }

    public boolean isErrorState() {
        return type == PlaceholderType.ERROR;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public String toString() {
        return message;
    }
}
