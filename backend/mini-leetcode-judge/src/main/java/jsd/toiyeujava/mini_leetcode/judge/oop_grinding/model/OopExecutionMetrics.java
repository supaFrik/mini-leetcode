package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

/**
 * Số đo thật của một lần chạy mã nguồn OOP trong sandbox Docker.
 *
 * <p>Với bài {@code OOP_GRINDING}, quá trình chấm điểm chạy harness (unit test,
 * socket scenario) nhưng phần đo số liệu cần một container riêng để đọc được
 * {@code cgroup memory.peak} và wall-clock thật. Record này là kết quả của
 * lần chạy đó.
 *
 * <p>Các trường đo có thể là {@code null} khi không đo được (Docker không khả dụng,
 * container bị kill trước khi báo sentinel...). Khi đó phải ghi {@code null} vào
 * DB chứ không phải {@code 0}, vì {@code 0} bị UI hiển thị là "0 KB" gây hiểu nhầm.
 *
 * @param runtimeMs    wall-clock của container (ms), {@code null} nếu không đo được
 * @param peakMemoryKb peak memory của cgroup (KB), {@code null} nếu không đo được
 * @param exitCode     mã thoát của tiến trình trong container
 * @param stdout       stdout của tiến trình (đã giới hạn độ dài)
 * @param stderr       stderr đã loại bỏ sentinel bộ nhớ
 * @param timedOut     container có bị giết vì hết thời gian không
 * @param oomKilled    container có bị OOM killer giết không
 * @param truncated    output có bị cắt cụt vì vượt quá giới hạn không
 * @param measured     {@code true} khi số đo đã được thực hiện trong container
 * @param errorMessage thông báo lỗi khi không thể chạy (VD: Docker không khả dụng)
 */
public record OopExecutionMetrics(
        Integer runtimeMs,
        Integer peakMemoryKb,
        Integer exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean oomKilled,
        boolean truncated,
        boolean measured,
        String errorMessage
) {

    /** Số đo không thực hiện được (Docker không khả dụng, lỗi hệ thống file...). */
    public static OopExecutionMetrics unavailable(String errorMessage) {
        return new OopExecutionMetrics(null, null, null, "", "", false, false, false, false, errorMessage);
    }
}
