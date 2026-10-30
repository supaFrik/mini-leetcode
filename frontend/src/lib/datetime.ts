/**
 * Parse một chuỗi thời gian ISO-8601 của API về epoch milliseconds.
 *
 * Server lưu mọi mốc thời gian theo UTC và (theo `JacksonConfig`) xuất ra JSON
 * kèm hậu tố `Z`, ví dụ `"2026-10-29T20:43:40Z"`. `Date.parse` xử lý đúng chuỗi này.
 *
 * Tuy nhiên nếu một payload cũ hoặc một endpoint khác trả về `LocalDateTime` KHÔNG
 * kèm múi giờ (ví dụ `"2026-10-29T20:43:40"`), `Date.parse` sẽ hiểu là giờ địa
 * phương của máy thí sinh. Với thí sinh ở UTC+7, mốc kết thúc kỳ thi 20:43 UTC bị
 * hiểu thành 20:43 giờ local → đã qua 7 tiếng → đồng hồ đếm ngược về 0 ngay lập tức
 * và bài thi bị khoá dù kỳ thi còn đang chạy.
 *
 * Hàm này chuẩn hoá mọi chuỗi không có múi giờ thành UTC trước khi parse, nên
 * client luôn diễn giải mốc thời gian theo đúng giờ server.
 */
export function parseApiDate(value?: string | null): number {
  if (!value) return NaN;
  const trimmed = value.trim();
  if (!trimmed) return NaN;

  // Đã có múi giờ (Z hoặc +HH:MM) -> Date.parse xử lý đúng, không cần can thiệp.
  const hasZone = /(?:Z|z|[+-]\d{2}:?\d{2})$/.test(trimmed);
  const normalized = hasZone
    ? trimmed
    : // Không có múi giờ -> coi như UTC (khớp với cách server lưu trữ).
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(trimmed)
      ? `${trimmed}Z`
      : trimmed;

  const parsed = Date.parse(normalized);
  return Number.isNaN(parsed) ? Date.parse(trimmed) : parsed;
}
