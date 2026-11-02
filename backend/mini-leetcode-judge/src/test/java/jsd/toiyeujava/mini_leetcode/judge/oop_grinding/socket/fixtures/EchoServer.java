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

public class EchoServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: EchoServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("SERVER_READY_PORT:" + serverSocket.getLocalPort());

            Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));

            while (!serverSocket.isClosed()) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (Exception e) {
                    break;
                }

                pool.submit(() -> handleClient(clientSocket, serverSocket));
            }
        } catch (Exception e) {
            System.err.println("EchoServer error: " + e.getMessage());
            System.exit(2);
        } finally {
            pool.shutdown();
        }
    }

    private static void handleClient(Socket socket, ServerSocket serverSocket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

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
