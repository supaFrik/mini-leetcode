package jsd.toiyeujava.mini_leetcode.console;

import jakarta.servlet.ServletContext;
import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class ConsoleWebSocketConfig implements WebSocketConfigurer {

    private final ConsoleWebSocketHandler handler;
    private final ConsoleGatewayProperties props;

    public ConsoleWebSocketConfig(ConsoleWebSocketHandler handler, ConsoleGatewayProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Mọi origin: xác thực bằng JWT trong frame đầu tiên, không dùng cookie ⇒ không có rủi ro
        // cross-site WebSocket hijacking. Cần thiết vì trình duyệt đi qua rewrite của Next (origin :3000).
        registry.addHandler(handler, "/api/v1/console/ws").setAllowedOriginPatterns("*");
    }

    /**
     * Cho phép frame {@code start} (chứa mã nguồn) lớn tới {@code maxMessageBytes}.
     * Bỏ qua khi không có WebSocket container thật (MockMvc trong @SpringBootTest), nếu không
     * cả context test sẽ lỗi "Attribute 'jakarta.websocket.server.ServerContainer' not found".
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean() {
            private boolean available;

            @Override
            public void setServletContext(ServletContext servletContext) {
                available = servletContext.getAttribute("jakarta.websocket.server.ServerContainer") != null;
                super.setServletContext(servletContext);
            }

            @Override
            public void afterPropertiesSet() {
                if (available) {
                    super.afterPropertiesSet();
                }
            }
        };
        container.setMaxTextMessageBufferSize(props.getMaxMessageBytes());
        return container;
    }
}
