package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.net.Socket;

public class HangingClient {
    public static void main(String[] args) {
        String host = args.length >= 2 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.parseInt(args[0]);

        try (Socket socket = new Socket(host, port)) {
            System.out.println("HANGING_CLIENT_CONNECTED");
            // Hang indefinitely to test socket scenario timeout
            Thread.sleep(60_000L);
        } catch (Exception ignored) {
        }
    }
}
