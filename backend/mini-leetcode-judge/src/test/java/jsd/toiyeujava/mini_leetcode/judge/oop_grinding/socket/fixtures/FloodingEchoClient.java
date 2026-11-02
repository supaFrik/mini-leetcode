package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class FloodingEchoClient {
    public static void main(String[] args) {
        String host = args.length >= 2 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.parseInt(args[0]);
        String payload = args.length >= 3 ? args[2] : "HELLO";

        String fillerChunk = "Y".repeat(120);

        // Pre-connection flood (> 1MB across stdout & stderr)
        for (int i = 0; i < 3000; i++) {
            System.out.println("CLIENT_STDOUT_FLOOD_" + i + "_" + fillerChunk);
            System.err.println("CLIENT_STDERR_FLOOD_" + i + "_" + fillerChunk);
        }
        System.out.flush();
        System.err.flush();

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // In-flight flood while socket is connected
            for (int i = 0; i < 2000; i++) {
                System.out.println("CLIENT_STDOUT_INFLIGHT_" + i + "_" + fillerChunk);
                System.err.println("CLIENT_STDERR_INFLIGHT_" + i + "_" + fillerChunk);
            }
            System.out.flush();
            System.err.flush();

            out.println(payload);
            String response = in.readLine();
            System.out.println("CLIENT_ECHO_RESULT:" + response);
        } catch (Exception e) {
            System.err.println("FloodingEchoClient failure: " + e.getMessage());
            System.exit(2);
        }
        System.exit(0);
    }
}
