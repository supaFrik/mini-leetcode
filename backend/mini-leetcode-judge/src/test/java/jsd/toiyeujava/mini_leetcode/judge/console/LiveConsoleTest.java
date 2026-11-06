package jsd.toiyeujava.mini_leetcode.judge.console;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveConsoleTest {

    @Test
    void handshakeRequiresInternalToken() {
        ConsoleProperties props = new ConsoleProperties();
        props.setInternalToken("secret");
        var interceptor = new LiveConsoleConfig.TokenInterceptor(props);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(denied), null, new HashMap<>())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        MockHttpServletRequest wrong = new MockHttpServletRequest();
        wrong.addHeader(LiveConsoleConfig.INTERNAL_TOKEN_HEADER, "nope");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(wrong),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();

        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader(LiveConsoleConfig.INTERNAL_TOKEN_HEADER, "secret");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(ok),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isTrue();
    }

    @Test
    void firstFrameMustBeStart() throws Exception {
        LiveConsoleHandler handler = new LiveConsoleHandler(new ConsoleProperties(), null, null, null);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"stdin\",\"data\":\"5\"}"));

        verify(session).sendMessage(argThat(m -> ((String) m.getPayload()).contains("Invalid console message")));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void runScriptExecsMainClassAndRejectsShellCharacters() {
        assertThat(LiveConsoleRun.runScript("com.example.Main", 256))
                .contains("exec java -Xmx192m")
                .contains("-cp /workspace/classes com.example.Main");
        assertThatThrownBy(() -> LiveConsoleRun.runScript("Main; rm -rf /", 256))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
