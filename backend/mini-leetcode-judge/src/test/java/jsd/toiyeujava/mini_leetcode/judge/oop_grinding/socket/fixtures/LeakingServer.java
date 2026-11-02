package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeakingServer {
    public static final List<Socket> LEAKED_SOCKETS = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        if (args.length < 1) {
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("LEAKING_SERVER_READY:" + serverSocket.getLocalPort());
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                LEAKED_SOCKETS.add(client);

                new Thread(() -> {
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                        PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                        String line = in.readLine();
                        if (line != null) {
                            out.println(line); // Correct echo
                        }
                        // Intentionally keep client socket open in sleep state without closing!
                        Thread.sleep(60_000L);
                    } catch (Exception ignored) {
                    }
                }).start();
            }
        } catch (Exception ignored) {
        }
    }
}
