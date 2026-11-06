package jsd.toiyeujava.mini_leetcode.judge.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * WebSocket nội bộ của console live. Frame đầu tiên phải là {@code start}
 * ({@code userId, problemId, files}); sau đó chỉ nhận {@code stdin}, {@code eof}, {@code kill}.
 */
@Slf4j
@Component
public class LiveConsoleHandler extends TextWebSocketHandler {

    private static final String RUN_ATTR = "liveConsoleRun";

    private final ConsoleProperties props;
    private final OopGrindingCompiler compiler;
    private final MultiFileSourceExtractor extractor;
    private final OopContainerExecutor containerExecutor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Semaphore slots;
    private final ExecutorService runners = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "live-console");
        t.setDaemon(true);
        return t;
    });

    public LiveConsoleHandler(ConsoleProperties props, OopGrindingCompiler compiler,
                              MultiFileSourceExtractor extractor, OopContainerExecutor containerExecutor) {
        this.props = props;
        this.compiler = compiler;
        this.extractor = extractor;
        this.containerExecutor = containerExecutor;
        this.slots = new Semaphore(props.getMaxConcurrent());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            reject(session, "Invalid console message");
            return;
        }
        String type = node.path("type").asText();
        LiveConsoleRun run = (LiveConsoleRun) session.getAttributes().get(RUN_ATTR);
        if (run != null) {
            switch (type) {
                case "stdin" -> run.stdin(node.path("data").asText(""));
                case "eof" -> run.eof();
                case "kill" -> run.kill("killed");
                default -> { } // start thứ hai / loại lạ: bỏ qua
            }
            return;
        }
        if (!"start".equals(type) || !node.path("files").isArray()) {
            reject(session, "Invalid console message");
            return;
        }
        if (!slots.tryAcquire()) {
            reject(session, "Console is busy, try again in a moment");
            return;
        }
        List<LiveConsoleRun.SourceFile> files = new ArrayList<>();
        node.path("files").forEach(f -> files.add(
                new LiveConsoleRun.SourceFile(f.path("path").asText(), f.path("content").asText(""))));
        LiveConsoleRun newRun = new LiveConsoleRun(
                new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024),
                props, compiler, extractor, containerExecutor, mapper);
        session.getAttributes().put(RUN_ATTR, newRun);
        log.info("Console run start userId={} problemId={} files={}",
                node.path("userId").asText(), node.path("problemId").asText(), files.size());
        runners.execute(() -> {
            try {
                newRun.run(files);
            } finally {
                slots.release();
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LiveConsoleRun run = (LiveConsoleRun) session.getAttributes().get(RUN_ATTR);
        if (run != null) {
            run.kill("killed");
        }
    }

    private void reject(WebSocketSession session, String message) throws IOException {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "error", "message", message))));
        session.close(CloseStatus.POLICY_VIOLATION);
    }
}
