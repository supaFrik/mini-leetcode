package jsd.toiyeujava.mini_leetcode.problem.authoring;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.exception.JudgeUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HttpJudgeAuthoringClient implements JudgeAuthoringClient {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final ConsoleGatewayProperties props;
    private final RestClient client;

    public HttpJudgeAuthoringClient(ConsoleGatewayProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        // Mỗi testcase là một container (JVM khởi động ~1s) ⇒ 100 testcase có thể mất vài phút.
        factory.setReadTimeout(10 * 60_000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ValidationResult validateGradingConfig(String gradingConfig) {
        return post("/internal/authoring/grading-config/validate",
                Map.of("gradingConfig", gradingConfig == null ? "" : gradingConfig), ValidationResult.class);
    }

    @Override
    public RunResult runReference(List<SourceFile> files, List<CaseInput> inputs, long timeLimitMs, int memoryLimitMb) {
        return post("/internal/authoring/reference-run",
                Map.of("files", files, "inputs", inputs, "timeLimitMs", timeLimitMs, "memoryLimitMb", memoryLimitMb),
                RunResult.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        String url = props.getJudgeHttpUrl().replaceAll("/+$", "") + path;
        try {
            T result = client.post().uri(url)
                    .header(TOKEN_HEADER, props.getInternalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (result == null) {
                throw new JudgeUnavailableException("Judge returned an empty response");
            }
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400) {
                throw new IllegalArgumentException(messageOf(e));
            }
            if (e.getStatusCode().value() == 503) {
                throw new JudgeUnavailableException(messageOf(e));
            }
            log.error("Judge authoring call {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new JudgeUnavailableException("Judge service error (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            log.error("Cannot reach judge at {}: {}", url, e.toString());
            throw new JudgeUnavailableException("Judge service is unavailable", e);
        }
    }

    private static String messageOf(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        int at = body.indexOf("\"message\":\"");
        if (at >= 0) {
            int start = at + 11;
            int end = body.indexOf('"', start);
            if (end > start) {
                return body.substring(start, end);
            }
        }
        return "Judge rejected the request (" + e.getStatusCode().value() + ")";
    }
}
