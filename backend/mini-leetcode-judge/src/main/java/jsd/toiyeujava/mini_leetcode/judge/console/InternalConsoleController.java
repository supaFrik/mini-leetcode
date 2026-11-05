package jsd.toiyeujava.mini_leetcode.judge.console;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint NỘI BỘ của judge worker cho console OOP.
 *
 * <p>Chỉ được gọi từ {@code mini-leetcode-api} qua docker network nội bộ, kèm
 * header {@code X-Internal-Token}. Judge worker KHÔNG publish port ra host.
 *
 * <p>Trả về 401 nếu thiếu/sai token, 503 nếu Docker không khả dụng.
 */
@Slf4j
@RestController
@RequestMapping("/internal/console")
public class InternalConsoleController {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ConsoleSessionService consoleSessionService;
    private final ConsoleProperties properties;

    public InternalConsoleController(ConsoleSessionService consoleSessionService, ConsoleProperties properties) {
        this.consoleSessionService = consoleSessionService;
        this.properties = properties;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestBody ConsoleRequest request) {

        if (!isAuthorized(token)) {
            log.warn("Rejected console request with invalid internal token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid internal token"));
        }

        ConsoleResponse response = consoleSessionService.execute(request);

        if (response.errorMessage() != null && response.exitCode() == null) {
            String message = response.errorMessage();
            if (message.contains("Docker sandbox is unavailable")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", message));
            }
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        return ResponseEntity.ok(response);
    }

    private boolean isAuthorized(String token) {
        String expected = properties.getInternalToken();
        if (expected == null || expected.isBlank() || token == null) {
            return false;
        }
        // So sánh constant-time để không rò rỉ thông tin qua thời gian phản hồi.
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
