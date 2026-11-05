package jsd.toiyeujava.mini_leetcode.console.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.entity.ConsoleMode;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.SandboxUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client gọi judge worker qua HTTP nội bộ.
 *
 * <p>Judge worker chạy trong docker network nội bộ (KHÔNG publish port ra host) và
 * yêu cầu header {@code X-Internal-Token}.
 */
@Slf4j
@Component
public class JudgeWorkerClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ConsoleGatewayProperties properties;
    private final RestClient restClient;

    public JudgeWorkerClient(ConsoleGatewayProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        String baseUrl = normalizeBaseUrl(properties.getJudgeBaseUrl());
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private String normalizeBaseUrl(String raw) {
        String url = (raw == null || raw.isBlank()) ? "http://localhost:8081" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Thực thi một lệnh console trên judge worker.
     *
     * @throws SandboxUnavailableException      khi Docker/judge worker không khả dụng
     * @throws ConsoleRateLimitExceededException khi vượt giới hạn lệnh của phiên
     */
    public JudgeWorkerResponse execute(JudgeWorkerRequest request) {
        String token = properties.getInternalToken();
        if (token == null || token.isBlank()) {
            throw new SandboxUnavailableException(
                    "Console is not configured: internal token is empty (Docker sandbox is unavailable)");
        }
        try {
            JudgeWorkerResponse response = restClient.post()
                    .uri("/internal/console/execute")
                    .header(INTERNAL_TOKEN_HEADER, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 503, (req, res) -> {
                        throw new SandboxUnavailableException(extractMessage(res));
                    })
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        throw new ConsoleRateLimitExceededException(extractMessage(res));
                    })
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (req, res) -> {
                        throw new SandboxUnavailableException(
                                "Console worker rejected the request: " + extractMessage(res));
                    })
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new SandboxUnavailableException(
                                "Console worker returned an error: " + extractMessage(res));
                    })
                    .body(JudgeWorkerResponse.class);
            return response == null ? JudgeWorkerResponse.empty() : response;
        } catch (SandboxUnavailableException | ConsoleRateLimitExceededException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Failed to reach judge worker at {}: {}", properties.getJudgeBaseUrl(), e.getMessage());
            throw new SandboxUnavailableException(
                    "Docker sandbox is unavailable: judge worker is unreachable (" + e.getMessage() + ")");
        }
    }

    private String extractMessage(Object response) {
        if (response instanceof org.springframework.http.client.ClientHttpResponse clientResponse) {
            try {
                String body = new String(clientResponse.getBody().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (body.contains("\"message\"")) {
                    int idx = body.indexOf("\"message\"");
                    int colon = body.indexOf(':', idx);
                    int start = body.indexOf('"', colon);
                    int end = body.indexOf('"', start + 1);
                    if (start > 0 && end > start) {
                        return body.substring(start + 1, end);
                    }
                }
                if (!body.isBlank()) {
                    return body.length() > 500 ? body.substring(0, 500) : body;
                }
            } catch (Exception ignored) {
                // Không đọc được body ⇒ dùng thông báo mặc định bên dưới.
            }
        }
        return "Docker sandbox is unavailable";
    }

    /** Payload gửi sang judge worker. */
    public record JudgeWorkerRequest(
            Long userId,
            Long problemId,
            List<JudgeWorkerFile> files,
            String input,
            String expression,
            ConsoleMode mode
    ) {
        public JudgeWorkerRequest {
            files = files == null ? List.of() : List.copyOf(files);
            input = input == null ? "" : input;
            expression = expression == null ? "" : expression;
            mode = mode == null ? ConsoleMode.EVALUATE : mode;
        }
    }

    /** Một file nguồn gửi sang judge worker. */
    public record JudgeWorkerFile(String path, String content) {
    }

    /** Response của judge worker. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JudgeWorkerResponse(
            String stdout,
            String stderr,
            Integer exitCode,
            Integer runtimeMs,
            Integer peakMemoryKb,
            String compileErrors,
            boolean truncated,
            boolean timedOut,
            String errorMessage
    ) {
        public static JudgeWorkerResponse empty() {
            return new JudgeWorkerResponse("", "", null, null, null, null, false, false, null);
        }
    }
}
