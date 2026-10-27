package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Phán quyết kết quả nộp bài (Online Judge Verdict).
 * 
 * TODO (Step 2): Định nghĩa các giá trị Verdict chuẩn của hệ thống Online Judge:
 *   - AC: Accepted (Toàn bộ testcase chính xác)
 *   - WA: Wrong Answer (Kết quả output thực tế khác output mong đợi)
 *   - TLE: Time Limit Exceeded (Quá giới hạn thời gian CPU)
 *   - MLE: Memory Limit Exceeded (Vượt quá giới hạn RAM 256MB)
 *   - CE: Compilation Error (Lỗi biên dịch mã nguồn)
 *   - RE: Runtime Error (Exception trong quá trình thực thi: NPE, IndexOutOfBounds, Crash)
 *   - SV: Sandbox Violation (Vi phạm an toàn hệ thống: gọi Socket mạng, Fork bomb, truy cập file cấm)
 *   - FLAGGED_CHEATING: Nghi ngờ gian lận qua phân tích hành vi nộp bài (Server-side proctoring)
 * 
 * TODO (Step 3): Thêm các trường dữ liệu và method bổ trợ cho Enum:
 *   - private final String description;
 *   - Constructor nhận description
 *   - Getter cho description
 *   - Helper method: `boolean isAccepted()` trả về true nếu là AC
 * 
 * Câu hỏi phản biện cần suy nghĩ trước khi code:
 * - Khi áp dụng chính sách "Fail-Fast", nếu bài nộp có 10 testcase và testcase #2 bị TLE,
 *   verdict cuối cùng của cả submission là gì? Các testcase còn lại có được chạy tiếp không?
 * - Giữa TLE và MLE, nếu một chương trình Java bị OutOfMemoryError khiến process bị JVM kill,
 *   Docker Sandbox sẽ ghi nhận Exit Code gì? Làm sao phân biệt CE vs RE vs MLE?
 */
public enum SubmissionVerdict {
    AC("Accepted"),
    WA("Wrong Answer"),
    TLE("Time Limit Exceeded"),
    MLE("Memory Limit Exceeded"),
    CE("Compilation Error"),
    RE("Runtime Error"),
    SV("Sandbox Violation"),
    FLAGGED_CHEATING("Flagged as Cheating");

    private final String description;

    SubmissionVerdict(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAccepted() {
        return this == AC;
    }
}
