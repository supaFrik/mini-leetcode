package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixture: High-concurrency echo server capable of handling simultaneous bursts
 * from 10+ concurrent clients with zero dropped connections and no deadlocks.
 */
public class ConcurrentEchoServer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ConcurrentEchoServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        // Dedicated thread pool sized to comfortably absorb concurrent bursts
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger activeConnections = new AtomicInteger(0);

        try (ServerSocket serverSocket = new ServerSocket(port, 100, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("CONCURRENT_SERVER_READY:" + serverSocket.getLocalPort());
            System.out.flush();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                pool.shutdownNow();
            }));

            while (!serverSocket.isClosed()) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                } catch (Exception e) {
                    break;
                }

                activeConnections.incrementAndGet();
                pool.submit(() -> handleClient(clientSocket, serverSocket, activeConnections));
            }
        } catch (Exception e) {
            System.err.println("ConcurrentEchoServer error: " + e.getMessage());
            System.exit(2);
        } finally {
            pool.shutdown();
        }
    }

    private static void handleClient(Socket socket, ServerSocket serverSocket, AtomicInteger activeConnections) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();
                if ("SHUTDOWN".equalsIgnoreCase(trimmed)) {
                    out.println("BYE");
                    out.flush();
                    try {
                        serverSocket.close();
                    } catch (Exception ignored) {
                    }
                    break;
                }
                // Echo line back immediately
                out.println(line);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            activeConnections.decrementAndGet();
        }
    }
}
