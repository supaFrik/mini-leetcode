package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class HangingServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("HANGING_SERVER_READY:" + serverSocket.getLocalPort());
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                new Thread(() -> {
                    try {
                        // Accept connection but hang without reading or writing response
                        Thread.sleep(60_000L);
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            }
        } catch (Exception ignored) {
        }
    }
}
