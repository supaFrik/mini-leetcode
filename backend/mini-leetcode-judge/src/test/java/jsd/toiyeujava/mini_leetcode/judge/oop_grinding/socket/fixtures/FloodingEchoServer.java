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

public class FloodingEchoServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: FloodingEchoServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        ExecutorService pool = Executors.newCachedThreadPool();

        // 1. Pre-connection spam: write > 1MB to stdout and stderr
        String fillerChunk = "X".repeat(120);
        for (int i = 0; i < 5000; i++) {
            System.out.println("STDOUT_PRE_FLOOD_" + i + "_" + fillerChunk);
            System.err.println("STDERR_PRE_FLOOD_" + i + "_" + fillerChunk);
        }
        System.out.flush();
        System.err.flush();

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("SERVER_READY_PORT:" + serverSocket.getLocalPort());
            System.out.flush();

            Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));

            while (!serverSocket.isClosed()) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (Exception e) {
                    break;
                }

                pool.submit(() -> handleClientWithFlooding(clientSocket, serverSocket, fillerChunk));
            }
        } catch (Exception e) {
            System.err.println("FloodingEchoServer error: " + e.getMessage());
            System.exit(2);
        } finally {
            pool.shutdown();
        }
    }

    private static void handleClientWithFlooding(Socket socket, ServerSocket serverSocket, String fillerChunk) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // In-flight flood during client handling
            for (int i = 0; i < 5000; i++) {
                System.out.println("STDOUT_IN_FLIGHT_FLOOD_" + i + "_" + fillerChunk);
                System.err.println("STDERR_IN_FLIGHT_FLOOD_" + i + "_" + fillerChunk);
            }
            System.out.flush();
            System.err.flush();

            String line;
            while ((line = in.readLine()) != null) {
                if ("SHUTDOWN".equalsIgnoreCase(line.trim())) {
                    out.println("BYE");
                    try {
                        serverSocket.close();
                    } catch (Exception ignored) {
                    }
                    break;
                }
                out.println(line);
            }
        } catch (Exception ignored) {
        }
    }
}
