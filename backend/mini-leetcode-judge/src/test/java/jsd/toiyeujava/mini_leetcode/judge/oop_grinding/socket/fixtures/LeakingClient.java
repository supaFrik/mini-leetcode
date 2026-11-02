package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Fixture: Client that successfully completes expected data exchange
 * but deliberately leaves the TCP socket unclosed and hangs the process
 * instead of exiting, to test harness resource leak detection.
 */
public class LeakingClient {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: LeakingClient [host] <port> [payload]");
            System.exit(1);
        }

        String host = args.length >= 2 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.parseInt(args[0]);
        String payload = args.length >= 3 ? args[2] : "HELLO";

        try {
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Valid exchange: send payload and read response
            out.println(payload);
            String response = in.readLine();
            System.out.println("LEAKING_CLIENT_RECEIVED:" + response);

            // Deliberately DO NOT CLOSE socket and DO NOT EXIT
            // Keep socket open in ESTABLISHED / CLOSE_WAIT state
            Thread.sleep(60_000L);

        } catch (Exception e) {
            System.err.println("LeakingClient error: " + e.getMessage());
            System.exit(2);
        }
    }
}
