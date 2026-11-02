package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MismatchServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("MISMATCH_SERVER_READY:" + serverSocket.getLocalPort());
            while (!serverSocket.isClosed()) {
                try (Socket client = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    in.readLine();
                    out.println("CORRUPTED_PROTOCOL_DATA_ERROR_999");
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
