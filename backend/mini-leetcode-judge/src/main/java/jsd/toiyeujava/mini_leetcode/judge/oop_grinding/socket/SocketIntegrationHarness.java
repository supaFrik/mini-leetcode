package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import jakarta.annotation.PreDestroy;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.ProcessLauncher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
public class SocketIntegrationHarness {

    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    public static final int DEFAULT_SCENARIO_TIMEOUT_MS = 3000;
    public static final int MAX_OUTPUT_CAPTURE_BYTES = 32768;

    private final ProcessLauncher processLauncher;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "socket-harness-io");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        ioExecutor.shutdownNow();
        try {
            if (!ioExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                log.warn("ioExecutor did not terminate within 1s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public SocketIntegrationHarness() {
        this(ProcessBuilder::start);
    }

    @Autowired
    public SocketIntegrationHarness(@Autowired(required = false) ProcessLauncher processLauncher) {
        this.processLauncher = processLauncher != null ? processLauncher : ProcessBuilder::start;
    }

    public ExecutionReport.SocketIntegrationPhase runScenarios(
            Path classesDir,
            HarnessConfig.SocketIntegrationConfig config,
            HarnessConfig.TimeoutBudget budget) {

        if (config == null || !config.isEnabled() || config.getScenarios() == null || config.getScenarios().isEmpty()) {
            double weight = config != null ? config.getWeight() : 0.0;
            return ExecutionReport.SocketIntegrationPhase.builder()
                    .score(0.0)
                    .weight(weight)
                    .weightedScore(0.0)
                    .summary("Socket integration skipped or no scenarios configured")
                    .scenarios(Collections.emptyList())
                    .build();
        }

        double phaseWeight = config.getWeight() > 0.0 ? config.getWeight() : 0.30;
        List<ExecutionReport.SocketScenarioResult> scenarioResults = new ArrayList<>();

        int defaultTimeout = (budget != null && budget.getSocketScenarioTimeoutSeconds() > 0)
                ? budget.getSocketScenarioTimeoutSeconds() * 1000
                : DEFAULT_SCENARIO_TIMEOUT_MS;

        for (SocketScenarioConfig scenario : config.getScenarios()) {
            int timeoutMs = scenario.getTimeoutMs() > 0 ? scenario.getTimeoutMs() : defaultTimeout;
            SocketRole role = scenario.getRole() != null ? scenario.getRole() : SocketRole.CLIENT;

            ExecutionReport.SocketScenarioResult result;
            try {
                if (role == SocketRole.CLIENT) {
                    result = runClientAssessment(classesDir, scenario, timeoutMs);
                } else {
                    result = runServerAssessment(classesDir, scenario, timeoutMs);
                }
            } catch (Exception e) {
                log.error("Unhandled error while evaluating socket scenario {}", scenario.getScenarioName(), e);
                result = ExecutionReport.SocketScenarioResult.failure(
                        scenario.getScenarioName(),
                        SocketVerdict.SCENARIO_TIMEOUT,
                        "Scenario execution error: " + e.getMessage()
                );
            }
            scenarioResults.add(result);
        }

        double avgScore = scenarioResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .average()
                .orElse(0.0);
        double phaseScore = Math.round(avgScore * 10.0) / 10.0;
        double weightedScore = Math.round(phaseScore * phaseWeight * 10.0) / 10.0;
        long passedCount = scenarioResults.stream().filter(ExecutionReport.SocketScenarioResult::isPassed).count();

        return ExecutionReport.SocketIntegrationPhase.builder()
                .score(phaseScore)
                .weight(phaseWeight)
                .weightedScore(weightedScore)
                .summary("Executed " + scenarioResults.size() + " scenarios (" + passedCount + " passed)")
                .scenarios(scenarioResults)
                .build();
    }

    public ExecutionReport.SocketScenarioResult runClientAssessment(
            Path classesDir,
            SocketScenarioConfig scenario,
            int timeoutMs) {

        int connectTimeout = scenario.getConnectTimeoutMs() > 0
                ? scenario.getConnectTimeoutMs()
                : DEFAULT_CONNECT_TIMEOUT_MS;

        ServerSocket serverSocket = null;
        Process clientProcess = null;
        try {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            int assignedPort = serverSocket.getLocalPort();
            serverSocket.setSoTimeout(connectTimeout);

            final ServerSocket finalServerSocket = serverSocket;
            CompletableFuture<Socket> acceptFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return finalServerSocket.accept();
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, ioExecutor);

            List<String> clientArgs = List.of(InetAddress.getLoopbackAddress().getHostAddress(), String.valueOf(assignedPort));
            clientProcess = launchJavaProcess(
                    classesDir,
                    scenario.getEffectiveTargetClass(),
                    clientArgs,
                    scenario.getExtraArgs()
            );
            ProcessOutputCapture outputCapture = new ProcessOutputCapture(clientProcess, ioExecutor);

            long startNano = System.nanoTime();
            long deadlineNano = startNano + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            Socket clientSocket = null;
            long connectDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(connectTimeout);
            while (System.nanoTime() < connectDeadline) {
                if (acceptFuture.isDone()) {
                    try {
                        clientSocket = acceptFuture.get();
                        break;
                    } catch (Exception e) {
                        break;
                    }
                }
                if (!clientProcess.isAlive()) {
                    int exit = clientProcess.exitValue();
                    return ExecutionReport.SocketScenarioResult.failure(
                            scenario.getScenarioName(),
                            SocketVerdict.CONNECTION_FAILURE,
                            "Client process exited prematurely with code " + exit + ": " + outputCapture.getCombinedOutput()
                    );
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (clientSocket == null && acceptFuture.isDone()) {
                try {
                    clientSocket = acceptFuture.getNow(null);
                } catch (Exception ignored) {
                }
            }
            if (clientSocket == null) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime());
                try {
                    clientProcess.waitFor(Math.max(200, Math.min(1000, remainingMs)), TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                if (!clientProcess.isAlive()) {
                    int exit = clientProcess.exitValue();
                    return ExecutionReport.SocketScenarioResult.failure(
                            scenario.getScenarioName(),
                            SocketVerdict.CONNECTION_FAILURE,
                            "Client process exited prematurely with code " + exit + ": " + outputCapture.getCombinedOutput()
                    );
                }
                return ExecutionReport.SocketScenarioResult.failure(
                        scenario.getScenarioName(),
                        SocketVerdict.CONNECTION_FAILURE,
                        "Client failed to connect to mock server within " + connectTimeout + "ms"
                );
            }

            final Socket activeClientSocket = clientSocket;
            try (activeClientSocket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(activeClientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(activeClientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                List<SocketStep> steps = scenario.resolveSteps();
                for (SocketStep step : steps) {
                    int remainingMs = (int) Math.min(timeoutMs, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime())));
                    activeClientSocket.setSoTimeout(remainingMs);

                    boolean receiveFirst = step.getReceiveFirst() != null ? step.getReceiveFirst() : true;
                    if (receiveFirst) {
                        if (step.getExpectedPayload() != null) {
                            String line = in.readLine();
                            if (line == null || !line.trim().equals(step.getExpectedPayload().trim())) {
                                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                                return ExecutionReport.SocketScenarioResult.builder()
                                        .scenarioName(scenario.getScenarioName())
                                        .passed(false)
                                        .verdict(SocketVerdict.PROTOCOL_MISMATCH)
                                        .score(0.0)
                                        .latencyMs(latencyMs)
                                        .error("Protocol mismatch: expected '" + step.getExpectedPayload() + "' but received '" + (line != null ? line : "<EOF>") + "'")
                                        .build();
                            }
                        }
                        if (step.getSendPayload() != null) {
                            out.println(step.getSendPayload());
                        }
                    } else {
                        if (step.getSendPayload() != null) {
                            out.println(step.getSendPayload());
                        }
                        if (step.getExpectedPayload() != null) {
                            String line = in.readLine();
                            if (line == null || !line.trim().equals(step.getExpectedPayload().trim())) {
                                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                                return ExecutionReport.SocketScenarioResult.builder()
                                        .scenarioName(scenario.getScenarioName())
                                        .passed(false)
                                        .verdict(SocketVerdict.PROTOCOL_MISMATCH)
                                        .score(0.0)
                                        .latencyMs(latencyMs)
                                        .error("Protocol mismatch: expected '" + step.getExpectedPayload() + "' but received '" + (line != null ? line : "<EOF>") + "'")
                                        .build();
                            }
                        }
                    }
                }

                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);

                boolean leakDetected = false;
                if (scenario.isExpectResourceCleanup()) {
                    try {
                        activeClientSocket.shutdownOutput();
                    } catch (IOException ignored) {
                    }

                    try {
                        long remainingMs = Math.max(1000, TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime()));
                        boolean exited = clientProcess.waitFor(remainingMs, TimeUnit.MILLISECONDS);
                        if (!exited) {
                            leakDetected = true;
                        } else {
                            try {
                                activeClientSocket.setSoTimeout(500);
                                int b = activeClientSocket.getInputStream().read();
                                if (b != -1) {
                                    leakDetected = true;
                                }
                            } catch (SocketTimeoutException ste) {
                                leakDetected = true;
                            } catch (IOException ignored) {
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        leakDetected = true;
                    }
                }

                if (leakDetected) {
                    return ExecutionReport.SocketScenarioResult.leak(
                            scenario.getScenarioName(),
                            latencyMs,
                            "Client failed to close socket or hung without terminating cleanly"
                    );
                }

                return ExecutionReport.SocketScenarioResult.success(scenario.getScenarioName(), latencyMs);

            } catch (SocketTimeoutException ste) {
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                return ExecutionReport.SocketScenarioResult.builder()
                        .scenarioName(scenario.getScenarioName())
                        .passed(false)
                        .verdict(SocketVerdict.SCENARIO_TIMEOUT)
                        .score(0.0)
                        .latencyMs(latencyMs)
                        .error("Scenario timed out after " + timeoutMs + "ms")
                        .build();
            }

        } catch (IOException ioe) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.CONNECTION_FAILURE,
                    "Client assessment IO error: " + ioe.getMessage()
            );
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }
            terminateProcess(clientProcess);
        }
    }

    public int allocateSafeEphemeralPort() throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try (ServerSocket probe = new ServerSocket()) {
                probe.setReuseAddress(true);
                probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
                int port = probe.getLocalPort();
                if (port >= 1024 && port <= 65535) {
                    return port;
                }
            } catch (IOException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw new IOException("Failed to allocate safe ephemeral port after 3 attempts", lastException);
        }
        throw new IOException("Failed to allocate safe ephemeral port in range [1024, 65535] after 3 attempts");
    }

    public ExecutionReport.SocketScenarioResult runServerAssessment(
            Path classesDir,
            SocketScenarioConfig scenario,
            int timeoutMs) {

        int assignedPort;
        try {
            assignedPort = allocateSafeEphemeralPort();
        } catch (IOException e) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.CONNECTION_FAILURE,
                    "Failed to allocate ephemeral port: " + e.getMessage()
            );
        }

        List<String> serverArgs = new ArrayList<>();
        serverArgs.add(String.valueOf(assignedPort));
        if (scenario.getServerArgs() != null && !scenario.getServerArgs().isBlank()) {
            serverArgs.addAll(List.of(scenario.getServerArgs().trim().split("\\s+")));
        }

        Process serverProcess = null;
        try {
            serverProcess = launchJavaProcess(
                    classesDir,
                    scenario.getEffectiveTargetClass(),
                    serverArgs,
                    scenario.getExtraArgs()
            );
            ProcessOutputCapture outputCapture = new ProcessOutputCapture(serverProcess, ioExecutor);

            // Readiness polling: 80 attempts x 25ms = 2000ms
            boolean ready = false;
            for (int attempt = 0; attempt < 80; attempt++) {
                if (!serverProcess.isAlive()) {
                    int exit = serverProcess.exitValue();
                    return ExecutionReport.SocketScenarioResult.failure(
                            scenario.getScenarioName(),
                            SocketVerdict.CONNECTION_FAILURE,
                            "Server process exited prematurely with code " + exit + ": " + outputCapture.getCombinedOutput()
                    );
                }
                try (Socket probeSocket = new Socket()) {
                    probeSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), assignedPort), 50);
                    ready = true;
                    break;
                } catch (IOException ignored) {
                    if (!serverProcess.isAlive()) {
                        int exit = serverProcess.exitValue();
                        return ExecutionReport.SocketScenarioResult.failure(
                                scenario.getScenarioName(),
                                SocketVerdict.CONNECTION_FAILURE,
                                "Server process exited prematurely with code " + exit + ": " + outputCapture.getCombinedOutput()
                        );
                    }
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!ready) {
                try {
                    serverProcess.waitFor(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                if (!serverProcess.isAlive()) {
                    int exit = serverProcess.exitValue();
                    return ExecutionReport.SocketScenarioResult.failure(
                            scenario.getScenarioName(),
                            SocketVerdict.CONNECTION_FAILURE,
                            "Server process exited prematurely with code " + exit + ": " + outputCapture.getCombinedOutput()
                    );
                }
                return ExecutionReport.SocketScenarioResult.failure(
                        scenario.getScenarioName(),
                        SocketVerdict.CONNECTION_FAILURE,
                        "Server failed to accept connections on " + InetAddress.getLoopbackAddress().getHostAddress() + ":" + assignedPort + " after 2000ms"
                );
            }

            int concurrency = scenario.getConcurrentClients();
            if (concurrency <= 1) {
                return runSingleServerMockClient(assignedPort, scenario, timeoutMs);
            } else {
                return runConcurrentServerMockClients(assignedPort, scenario, timeoutMs, concurrency);
            }

        } catch (IOException ioe) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.CONNECTION_FAILURE,
                    "Failed to launch student server: " + ioe.getMessage()
            );
        } finally {
            if (scenario.getShutdownPayload() != null && !scenario.getShutdownPayload().isBlank()) {
                sendShutdownSignal(assignedPort, scenario.getShutdownPayload());
            }
            terminateProcess(serverProcess);
        }
    }

    private ExecutionReport.SocketScenarioResult runSingleServerMockClient(
            int port,
            SocketScenarioConfig scenario,
            int timeoutMs) {

        long startNano = System.nanoTime();
        long deadlineNano = startNano + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), DEFAULT_CONNECT_TIMEOUT_MS);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            List<SocketStep> steps = scenario.resolveSteps();
            for (SocketStep step : steps) {
                int remainingMs = (int) Math.min(timeoutMs, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime())));
                socket.setSoTimeout(remainingMs);
                boolean receiveFirst = step.getReceiveFirst() != null ? step.getReceiveFirst() : false;
                if (receiveFirst) {
                    if (step.getExpectedPayload() != null) {
                        String line = in.readLine();
                        if (line == null || !line.trim().equals(step.getExpectedPayload().trim())) {
                            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                            return ExecutionReport.SocketScenarioResult.builder()
                                    .scenarioName(scenario.getScenarioName())
                                    .passed(false)
                                    .verdict(SocketVerdict.PROTOCOL_MISMATCH)
                                    .score(0.0)
                                    .latencyMs(latency)
                                    .error("Protocol mismatch: expected '" + step.getExpectedPayload() + "' but received '" + (line != null ? line : "<EOF>") + "'")
                                    .build();
                        }
                    }
                    if (step.getSendPayload() != null) {
                        out.println(step.getSendPayload());
                    }
                } else {
                    if (step.getSendPayload() != null) {
                        out.println(step.getSendPayload());
                    }
                    if (step.getExpectedPayload() != null) {
                        String line = in.readLine();
                        if (line == null || !line.trim().equals(step.getExpectedPayload().trim())) {
                            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                            return ExecutionReport.SocketScenarioResult.builder()
                                    .scenarioName(scenario.getScenarioName())
                                    .passed(false)
                                    .verdict(SocketVerdict.PROTOCOL_MISMATCH)
                                    .score(0.0)
                                    .latencyMs(latency)
                                    .error("Protocol mismatch: expected '" + step.getExpectedPayload() + "' but received '" + (line != null ? line : "<EOF>") + "'")
                                    .build();
                        }
                    }
                }
            }

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);

            boolean leakDetected = false;
            if (scenario.isExpectResourceCleanup()) {
                try {
                    socket.shutdownOutput();
                    socket.setSoTimeout(500);
                    int b = socket.getInputStream().read();
                    if (b != -1) {
                        leakDetected = true;
                    }
                } catch (SocketTimeoutException ste) {
                    leakDetected = true;
                } catch (IOException ignored) {
                }
            }

            if (leakDetected) {
                return ExecutionReport.SocketScenarioResult.leak(
                        scenario.getScenarioName(),
                        latencyMs,
                        "Server socket was not closed upon completion"
                );
            }

            return ExecutionReport.SocketScenarioResult.success(scenario.getScenarioName(), latencyMs);

        } catch (SocketTimeoutException ste) {
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            return ExecutionReport.SocketScenarioResult.builder()
                    .scenarioName(scenario.getScenarioName())
                    .passed(false)
                    .verdict(SocketVerdict.SCENARIO_TIMEOUT)
                    .score(0.0)
                    .latencyMs(latency)
                    .error("Scenario timed out after " + timeoutMs + "ms")
                    .build();
        } catch (IOException ioe) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.CONNECTION_FAILURE,
                    "Connection error: " + ioe.getMessage()
            );
        }
    }

    private ExecutionReport.SocketScenarioResult runConcurrentServerMockClients(
            int port,
            SocketScenarioConfig scenario,
            int timeoutMs,
            int concurrency) {

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch connectedLatch = new CountDownLatch(concurrency);
        CountDownLatch burstGate = new CountDownLatch(1);
        List<Future<MockClientTaskResult>> futures = new ArrayList<>();

        for (int i = 1; i <= concurrency; i++) {
            int clientId = i;
            futures.add(pool.submit(() -> runConcurrentMockClient(
                    clientId,
                    port,
                    scenario,
                    timeoutMs,
                    connectedLatch,
                    burstGate
            )));
        }

        try {
            boolean allConnected = connectedLatch.await(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!allConnected) {
                burstGate.countDown();
                pool.shutdownNow();
                return ExecutionReport.SocketScenarioResult.failure(
                        scenario.getScenarioName(),
                        SocketVerdict.CONNECTION_FAILURE,
                        "Failed to connect all " + concurrency + " clients within " + DEFAULT_CONNECT_TIMEOUT_MS + "ms"
                );
            }

            burstGate.countDown();

            List<MockClientTaskResult> results = new ArrayList<>();
            for (Future<MockClientTaskResult> future : futures) {
                results.add(future.get(timeoutMs + 2000L, TimeUnit.MILLISECONDS));
            }

            for (MockClientTaskResult res : results) {
                if (!res.passed) {
                    return ExecutionReport.SocketScenarioResult.builder()
                            .scenarioName(scenario.getScenarioName())
                            .passed(false)
                            .verdict(res.verdict)
                            .score(res.verdict != null ? res.verdict.getBaseScore() : 0.0)
                            .latencyMs(res.latencyMs)
                            .error("Client #" + res.clientId + " " + res.error)
                            .build();
                }
            }

            boolean anyLeak = results.stream().anyMatch(r -> r.leak);
            long avgLatency = (long) results.stream().mapToLong(r -> r.latencyMs).average().orElse(0.0);

            if (anyLeak) {
                return ExecutionReport.SocketScenarioResult.leak(
                        scenario.getScenarioName(),
                        avgLatency,
                        "One or more client connections were not released by server"
                );
            }

            return ExecutionReport.SocketScenarioResult.success(scenario.getScenarioName(), avgLatency);

        } catch (TimeoutException te) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.SCENARIO_TIMEOUT,
                    "Concurrent client pool execution timed out"
            );
        } catch (Exception e) {
            return ExecutionReport.SocketScenarioResult.failure(
                    scenario.getScenarioName(),
                    SocketVerdict.CONNECTION_FAILURE,
                    "Concurrent assessment error: " + e.getMessage()
            );
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private MockClientTaskResult runConcurrentMockClient(
            int clientId,
            int port,
            SocketScenarioConfig scenario,
            int timeoutMs,
            CountDownLatch connectedLatch,
            CountDownLatch burstGate) {

        long startNano = 0;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), DEFAULT_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(timeoutMs);

            connectedLatch.countDown();
            boolean burstReady = burstGate.await(5, TimeUnit.SECONDS);
            if (!burstReady) {
                return new MockClientTaskResult(clientId, false, false, SocketVerdict.SCENARIO_TIMEOUT, 0, "Burst gate timeout");
            }

            startNano = System.nanoTime();
            long deadlineNano = startNano + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            List<SocketStep> steps = scenario.resolveSteps();
            if (steps.isEmpty()) {
                int remainingMs = (int) Math.min(timeoutMs, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime())));
                socket.setSoTimeout(remainingMs);
                String req = "CLIENT_" + clientId + "_REQ";
                out.println(req);
                String resp = in.readLine();
                if (resp == null || !resp.trim().equals(req)) {
                    long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                    return new MockClientTaskResult(clientId, false, false, SocketVerdict.PROTOCOL_MISMATCH, latency,
                            "Protocol Mismatch: expected '" + req + "' but got '" + resp + "'");
                }
            } else {
                for (SocketStep step : steps) {
                    int remainingMs = (int) Math.min(timeoutMs, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNano - System.nanoTime())));
                    socket.setSoTimeout(remainingMs);
                    String send = step.getSendPayload();
                    if (send != null) {
                        send = send.replace("{clientId}", String.valueOf(clientId));
                        out.println(send);
                    }
                    if (step.getExpectedPayload() != null) {
                        String expected = step.getExpectedPayload().replace("{clientId}", String.valueOf(clientId));
                        String line = in.readLine();
                        if (line == null || !line.trim().equals(expected.trim())) {
                            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
                            return new MockClientTaskResult(clientId, false, false, SocketVerdict.PROTOCOL_MISMATCH, latency,
                                    "Protocol Mismatch: expected '" + expected + "' but got '" + line + "'");
                        }
                    }
                }
            }

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            boolean leak = false;
            if (scenario.isExpectResourceCleanup()) {
                try {
                    socket.shutdownOutput();
                    socket.setSoTimeout(500);
                    int b = socket.getInputStream().read();
                    if (b != -1) {
                        leak = true;
                    }
                } catch (SocketTimeoutException ste) {
                    leak = true;
                } catch (IOException ignored) {
                }
            }

            return new MockClientTaskResult(clientId, true, leak, leak ? SocketVerdict.RESOURCE_LEAK : SocketVerdict.SUCCESS, latencyMs, null);

        } catch (SocketTimeoutException ste) {
            long latency = startNano > 0 ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano) : 0;
            return new MockClientTaskResult(clientId, false, false, SocketVerdict.SCENARIO_TIMEOUT, latency, "Scenario Timeout");
        } catch (IOException ioe) {
            long latency = startNano > 0 ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano) : 0;
            String msg = ioe.getMessage() != null ? ioe.getMessage() : "Connection error";
            return new MockClientTaskResult(clientId, false, false, SocketVerdict.CONNECTION_FAILURE, latency, msg);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new MockClientTaskResult(clientId, false, false, SocketVerdict.SCENARIO_TIMEOUT, 0, "Interrupted");
        }
    }

    private void sendShutdownSignal(int port, String shutdownPayload) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(shutdownPayload);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception ignored) {
        }
    }

    private Process launchJavaProcess(
            Path classesDir,
            String targetClass,
            List<String> args,
            List<String> extraArgs) throws IOException {

        if (targetClass == null || targetClass.isBlank()) {
            throw new IllegalArgumentException("targetClass must not be null or blank");
        }
        if (targetClass.startsWith("-") || !targetClass.matches("^[a-zA-Z0-9_.$]+$")) {
            throw new IllegalArgumentException("Invalid target class name: " + targetClass);
        }

        String javaBin = ProcessHandle.current().info().command().orElseGet(() -> {
            String home = System.getProperty("java.home");
            if (home != null) {
                boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
                File j = new File(home + File.separator + "bin" + File.separator + "java" + (isWin ? ".exe" : ""));
                if (j.exists()) {
                    return j.getAbsolutePath();
                }
            }
            return "java";
        });

        StringBuilder cp = new StringBuilder();
        if (classesDir != null) {
            cp.append(classesDir.toAbsolutePath());
        }
        String currentCp = System.getProperty("java.class.path");
        if (currentCp != null && !currentCp.isBlank()) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparator);
            }
            cp.append(currentCp);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Djava.net.preferIPv4Stack=true");
        cmd.add("-Dserver.host=127.0.0.1");
        cmd.add("-cp");
        cmd.add(cp.toString());
        cmd.add(targetClass);
        if (args != null) {
            cmd.addAll(args);
        }
        if (extraArgs != null) {
            cmd.addAll(extraArgs);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        return processLauncher.launch(pb);
    }

    private void terminateProcess(Process process) {
        if (process == null) {
            return;
        }

        List<ProcessHandle> descendants = Collections.emptyList();
        try {
            descendants = process.descendants().toList();
        } catch (Throwable ignored) {
        }

        try {
            process.destroy();
            boolean exited = process.waitFor(500, TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        for (ProcessHandle ph : descendants) {
            try {
                if (ph.isAlive()) {
                    ph.destroyForcibly();
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            process.descendants().forEach(ph -> {
                try {
                    if (ph.isAlive()) {
                        ph.destroyForcibly();
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            if (process.getInputStream() != null) {
                process.getInputStream().close();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (process.getOutputStream() != null) {
                process.getOutputStream().close();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (process.getErrorStream() != null) {
                process.getErrorStream().close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static class ProcessOutputCapture {
        private final CompletableFuture<String> stdoutFuture;
        private final CompletableFuture<String> stderrFuture;
        private final ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

        ProcessOutputCapture(Process process, ExecutorService executor) {
            this.stdoutFuture = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream(), stdoutBuffer), executor);
            this.stderrFuture = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream(), stderrBuffer), executor);
        }

        String getStdout() {
            if (stdoutFuture.isDone()) {
                try {
                    return stdoutFuture.get();
                } catch (Exception ignored) {
                }
            }
            synchronized (stdoutBuffer) {
                return stdoutBuffer.toString(StandardCharsets.UTF_8);
            }
        }

        String getStderr() {
            if (stderrFuture.isDone()) {
                try {
                    return stderrFuture.get();
                } catch (Exception ignored) {
                }
            }
            synchronized (stderrBuffer) {
                return stderrBuffer.toString(StandardCharsets.UTF_8);
            }
        }

        String getCombinedOutput() {
            try {
                stderrFuture.get(300, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            try {
                stdoutFuture.get(300, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            String out = getStdout();
            String err = getStderr();
            if (err.isBlank()) return out;
            if (out.isBlank()) return err;
            return out + "\n" + err;
        }

        private static String readLimited(InputStream in, ByteArrayOutputStream buffer) {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        int room = MAX_OUTPUT_CAPTURE_BYTES - buffer.size();
                        if (room > 0) {
                            buffer.write(chunk, 0, Math.min(n, room));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private static class MockClientTaskResult {
        final int clientId;
        final boolean passed;
        final boolean leak;
        final SocketVerdict verdict;
        final long latencyMs;
        final String error;

        MockClientTaskResult(int clientId, boolean passed, boolean leak, SocketVerdict verdict, long latencyMs, String error) {
            this.clientId = clientId;
            this.passed = passed;
            this.leak = leak;
            this.verdict = verdict;
            this.latencyMs = latencyMs;
            this.error = error;
        }
    }
}
