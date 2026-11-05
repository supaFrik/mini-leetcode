package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Chế độ chạy của một lệnh console OOP.
 *
 * <p>Bài OOP có hai kiểchạy khác nhau, không thể gộp:
 * <ul>
 *   <li>{@link #EVALUATE} — đánh giá một biểu thức Java trên các lớp của học viên
 *       (REPL). Không quan tâm học viên có viết {@code main} hay không.</li>
 *   <li>{@link #PROGRAM} — chạy {@code public static void main(String[])} của học viên
 *       với nội dung ô nhập làm {@code stdin}.</li>
 * </ul>
 *
 * <p>Mặc định là {@link #EVALUATE} vì hầu hết bài OOP trong hệ thống chỉ yêu cầu
 * thiết kế lớp (không có {@code main}), và vì người học cần gõ thử biểu thức để
 * kiểm tra lớp vừa viết.
 */
public enum ConsoleMode {

    /** Đánh giá biểu thức (REPL) trên các lớp đã biên dịch của học viên. */
    EVALUATE,

    /** Chạy {@code main} của học viên với nội dung ô nhập làm stdin. */
    PROGRAM
}
