package jsd.toiyeujava.mini_leetcode.judge.console;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Đăng ký WebSocket nội bộ {@code /internal/console/ws}. Chỉ API gọi tới (docker network nội bộ),
 * handshake phải mang header {@code X-Internal-Token} đúng, nếu không trả 401.
 */
@Configuration
@EnableWebSocket
public class LiveConsoleConfig implements WebSocketConfigurer {

    public static final String PATH = "/internal/console/ws";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final LiveConsoleHandler handler;
    private final ConsoleProperties properties;

    public LiveConsoleConfig(LiveConsoleHandler handler, ConsoleProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH).addInterceptors(new TokenInterceptor(properties));
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxMessageBytes());
        return container;
    }

    /** Từ chối handshake khi thiếu/sai token nội bộ (so sánh constant-time). */
    record TokenInterceptor(ConsoleProperties properties) implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String expected = properties.getInternalToken();
            String actual = request.getHeaders().getFirst(INTERNAL_TOKEN_HEADER);
            boolean ok = expected != null && !expected.isBlank() && actual != null
                    && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8));
            if (!ok) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return ok;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
