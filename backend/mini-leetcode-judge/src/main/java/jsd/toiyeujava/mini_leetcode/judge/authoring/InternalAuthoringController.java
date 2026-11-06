package jsd.toiyeujava.mini_leetcode.judge.authoring;

import jsd.toiyeujava.mini_leetcode.judge.console.ConsoleProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ soạn bài nội bộ, CHỈ API gọi (docker network nội bộ, judge không publish port).
 * Mọi request phải mang header {@code X-Internal-Token} đúng (cùng token với console live).
 */
@RestController
@RequestMapping("/internal/authoring")
public class InternalAuthoringController {

    static final String TOKEN_HEADER = "X-Internal-Token";

    public record ValidateRequest(String gradingConfig) {
    }

    public record RunRequest(List<ReferenceRunService.SourceFile> files, List<ReferenceRunService.CaseInput> inputs,
                             Long timeLimitMs, Integer memoryLimitMb) {
    }

    private final GradingConfigValidator validator;
    private final ReferenceRunService referenceRunService;
    private final ConsoleProperties properties;

    public InternalAuthoringController(GradingConfigValidator validator, ReferenceRunService referenceRunService,
                                       ConsoleProperties properties) {
        this.validator = validator;
        this.referenceRunService = referenceRunService;
        this.properties = properties;
    }

    @PostMapping("/grading-config/validate")
    public ResponseEntity<?> validate(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                      @RequestBody ValidateRequest request) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GradingConfigValidator.Result result = validator.validate(request == null ? null : request.gradingConfig());
        return ResponseEntity.ok(Map.of(
                "valid", result.valid(),
                "errors", result.errors(),
                "ruleCount", result.ruleCount(),
                "socketScenarioCount", result.socketScenarioCount(),
                "totalRuleWeight", result.totalRuleWeight()));
    }

    @PostMapping("/reference-run")
    public ResponseEntity<?> run(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @RequestBody RunRequest request) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long timeLimit = request.timeLimitMs() != null && request.timeLimitMs() > 0 ? request.timeLimitMs() : 1000L;
        int memory = request.memoryLimitMb() != null && request.memoryLimitMb() >= 16 ? request.memoryLimitMb() : 256;
        try {
            return ResponseEntity.ok(referenceRunService.run(request.files(), request.inputs(), timeLimit, memory));
        } catch (ReferenceRunService.BusyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** So sánh constant-time; token cấu hình rỗng ⇒ từ chối tất cả. */
    private boolean authorized(String actual) {
        String expected = properties.getInternalToken();
        return expected != null && !expected.isBlank() && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
