package jsd.toiyeujava.mini_leetcode.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.security.CustomUserDetailsService;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.console.service.ConsoleService;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleDisabledException;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket công khai của console live ({@code /api/v1/console/ws}).
 *
 * <p>Frame đầu tiên {@code start} mang JWT (trình duyệt không đặt được header khi mở WebSocket,
 * và token trên URL sẽ lọt vào access log). Sau khi kiểm tra quyền, API mở WebSocket tới judge
 * worker và relay hai chiều; từ trình duyệt chỉ chuyển tiếp {@code stdin}/{@code eof}/{@code kill}.
 * Mỗi user chỉ có một lần chạy: Run mới đóng kết nối cũ ⇒ judge kill container cũ.
 */
@Slf4j
@Component
public class ConsoleWebSocketHandler extends TextWebSocketHandler {

    private static final String JUDGE = "judge";
    private static final String USER = "userId";

    private final JwtTokenProvider jwt;
    private final CustomUserDetailsService users;
    private final ConsoleService consoleService;
    private final ConsoleGatewayProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final Map<Long, WebSocketSession> activeByUser = new ConcurrentHashMap<>();

    public ConsoleWebSocketHandler(JwtTokenProvider jwt, CustomUserDetailsService users,
                                   ConsoleService consoleService, ConsoleGatewayProperties props) {
        this.jwt = jwt;
        this.users = users;
        this.consoleService = consoleService;
        this.props = props;
    }

    @Override
    protected void handleTextMessage(WebSocketSession browser, TextMessage message) throws IOException {
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            reject(browser, "Invalid console message");
            return;
        }
        String type = node.path("type").asText();
        WebSocketSession judge = (WebSocketSession) browser.getAttributes().get(JUDGE);
        if (judge != null) {
            forward(judge, type, node);
            return;
        }
        if (browser.getAttributes().containsKey(USER)) {
            return; // đang kết nối tới judge — bỏ qua frame tới sớm
        }
        if (!"start".equals(type)) {
            reject(browser, "Invalid console message");
            return;
        }
        start(browser, node);
    }

    private void start(WebSocketSession browser, JsonNode node) throws IOException {
        Long userId = authenticate(node.path("token").asText(null));
        if (userId == null) {
            reject(browser, "Please log in to use the console");
            return;
        }
        long problemId = node.path("problemId").asLong();
        List<ConsoleService.SourceFile> files = new ArrayList<>();
        node.path("files").forEach(f -> files.add(
                new ConsoleService.SourceFile(f.path("path").asText(null), f.path("content").asText(""))));
        List<ConsoleService.SourceFile> validated;
        try {
            validated = consoleService.prepareRun(userId, problemId, node.path("language").asText(), files);
        } catch (IllegalArgumentException | ResourceNotFoundException | ConsoleDisabledException
                 | ConsoleRateLimitExceededException | PayloadTooLargeException e) {
            reject(browser, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Console validation failed userId={} problemId={}", userId, problemId, e);
            reject(browser, "Console service is unavailable");
            return;
        }

        browser.getAttributes().put(USER, userId);
        WebSocketSession previous = activeByUser.put(userId, browser);
        if (previous != null && previous != browser) {
            closeQuietly(previous); // đóng ⇒ judge kill container của lần chạy trước
        }

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-Internal-Token", props.getInternalToken());
        WebSocketSession judge;
        try {
            judge = client.execute(new JudgeRelay(new ConcurrentWebSocketSessionDecorator(browser, 10_000, 512 * 1024)),
                    headers, URI.create(props.getJudgeWsUrl())).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Cannot reach judge console at {}: {}", props.getJudgeWsUrl(), e.toString());
            reject(browser, "Console service is unavailable");
            return;
        }
        judge.setTextMessageSizeLimit(props.getMaxMessageBytes());
        browser.getAttributes().put(JUDGE, judge);
        if (!browser.isOpen()) {
            closeQuietly(judge);
            return;
        }
        judge.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of(
                "type", "start", "userId", userId, "problemId", problemId, "files", validated))));
        log.info("Console run relayed userId={} problemId={} files={}", userId, problemId, validated.size());
    }

    /** Chỉ chuyển tiếp các loại frame an toàn; frame stdin quá lớn bị bỏ. */
    private void forward(WebSocketSession judge, String type, JsonNode node) throws IOException {
        Map<String, Object> frame;
        switch (type) {
            case "stdin" -> {
                String data = node.path("data").asText("");
                if (data.length() > props.getMaxStdinBytes()) {
                    return;
                }
                frame = Map.of("type", "stdin", "data", data);
            }
            case "eof", "kill" -> frame = Map.of("type", type);
            default -> {
                return;
            }
        }
        if (judge.isOpen()) {
            judge.sendMessage(new TextMessage(mapper.writeValueAsString(frame)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browser, CloseStatus status) {
        closeQuietly((WebSocketSession) browser.getAttributes().get(JUDGE));
        Object userId = browser.getAttributes().get(USER);
        if (userId != null) {
            activeByUser.remove(userId, browser);
        }
    }

    private Long authenticate(String token) {
        if (!StringUtils.hasText(token) || !jwt.validateToken(token)) {
            return null;
        }
        try {
            var user = users.loadUserByUsername(jwt.getUsernameFromToken(token));
            return user.isEnabled() && user.isAccountNonLocked() ? jwt.getUserIdFromToken(token) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void reject(WebSocketSession session, String message) throws IOException {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "error", "message", message))));
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    private static void closeQuietly(WebSocketSession session) {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignored) {
                // đã đóng
            }
        }
    }

    /** Judge → trình duyệt: chuyển nguyên frame; judge đóng ⇒ đóng trình duyệt. */
    private static final class JudgeRelay extends TextWebSocketHandler {
        private final WebSocketSession browser;

        JudgeRelay(WebSocketSession browser) {
            this.browser = browser;
        }

        @Override
        protected void handleTextMessage(WebSocketSession judge, TextMessage message) throws IOException {
            if (browser.isOpen()) {
                browser.sendMessage(message);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession judge, CloseStatus status) {
            closeQuietly(browser);
        }
    }
}
