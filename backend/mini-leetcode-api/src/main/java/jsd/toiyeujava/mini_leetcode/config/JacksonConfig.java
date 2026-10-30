package jsd.toiyeujava.mini_leetcode.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Cấu hình Jackson để ghi {@link LocalDateTime} ra JSON dưới dạng ISO-8601 có hậu tố
 * {@code "Z"} (UTC).
 *
 * <p>Cột {@code DATETIME} trong MySQL được lưu theo UTC (kết nối dùng
 * {@code serverTimezone=UTC}) và môi trường chạy cũng đặt múi giờ mặc định là UTC.
 *
 * <p>Nếu serialize {@code LocalDateTime} không kèm múi giờ (ví dụ
 * {@code "2026-10-29T20:43:40"}), trình duyệt sẽ hiểu chuỗi đó là giờ địa phương của
 * máy thí sinh. Với múi giờ UTC+7, kỳ thi kết thúc lúc 20:43 UTC sẽ bị hiểu là
 * 20:43 giờ local — tức đã kết thúc từ 7 tiếng trước. Hệ quả là đồng hồ đếm ngược
 * hiện {@code 00:00}, bật trạng thái "hết giờ" và khoá nút nộp bài của thí sinh.
 *
 * <p>Ghi kèm {@code "Z"} giúp mọi client (JavaScript, Java, Python) diễn giải thời điểm
 * một cách nhất quán và tuyệt đối.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeAsUtcCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class, new LocalDateTimeUtcSerializer());
    }

    /**
     * Ghi {@code LocalDateTime} (đang ở múi giờ UTC) thành chuỗi ISO-8601 có hậu tố
     * {@code "Z"}, ví dụ {@code "2026-10-29T20:43:40Z"}.
     */
    static final class LocalDateTimeUtcSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(value.toInstant(ZoneOffset.UTC).toString());
        }
    }
}
