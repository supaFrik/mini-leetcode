package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MidTransactionCrashServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MidTransactionCrashServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("SERVER_READY_PORT:" + serverSocket.getLocalPort());
            System.out.flush();

            Socket clientSocket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line = in.readLine();
            System.err.println("MidTransactionCrashServer received: '" + line + "' -> abruptly terminating with code 137!");
            System.err.flush();

            // Mid-transaction fatal termination
            Runtime.getRuntime().halt(137);

        } catch (Exception e) {
            System.err.println("MidTransactionCrashServer error: " + e.getMessage());
            System.exit(1);
        }
    }
}
