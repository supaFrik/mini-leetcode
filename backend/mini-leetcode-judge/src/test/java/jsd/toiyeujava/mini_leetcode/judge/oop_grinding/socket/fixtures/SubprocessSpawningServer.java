package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SubprocessSpawningServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: SubprocessSpawningServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        try {
            // Fork child subprocess
            String javaBin = ProcessHandle.current().info().command().orElse("java");
            String cp = System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", cp, LongLivingWorker.class.getName());
            Process child = pb.start();

            // Print child PID for test observation
            System.out.println("CHILD_PID:" + child.pid());
            System.out.flush();

            try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
                System.out.println("SERVER_READY_PORT:" + serverSocket.getLocalPort());
                System.out.flush();

                while (!serverSocket.isClosed()) {
                    Socket clientSocket;
                    try {
                        clientSocket = serverSocket.accept();
                    } catch (Exception e) {
                        break;
                    }

                    try (clientSocket;
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                        String line;
                        while ((line = in.readLine()) != null) {
                            if ("SHUTDOWN".equalsIgnoreCase(line.trim())) {
                                out.println("BYE");
                                serverSocket.close();
                                break;
                            }
                            out.println("ECHO:" + line);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("SubprocessSpawningServer error: " + e.getMessage());
            System.exit(2);
        }
    }
}
