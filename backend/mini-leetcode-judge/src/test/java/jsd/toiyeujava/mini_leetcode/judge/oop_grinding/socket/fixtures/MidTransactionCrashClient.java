package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MidTransactionCrashClient {
    public static void main(String[] args) {
        String host = args.length >= 2 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.parseInt(args[0]);

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Send handshake
            out.println("HELLO_BEFORE_CRASH");

            // Intentionally crash mid-transaction without clean shutdown
            System.err.println("MidTransactionCrashClient: Abruptly terminating process with exit code 137 mid-stream!");
            System.err.flush();
            Runtime.getRuntime().halt(137);
        } catch (Exception e) {
            System.err.println("MidTransactionCrashClient exception: " + e.getMessage());
            System.exit(1);
        }
    }
}
