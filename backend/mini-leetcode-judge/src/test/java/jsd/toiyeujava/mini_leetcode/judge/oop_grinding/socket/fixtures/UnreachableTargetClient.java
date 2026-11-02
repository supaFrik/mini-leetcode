package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Fixture: Client that attempts to connect to an unreachable or closed port
 * rather than the port assigned by the mock server, testing client connection failure handling.
 */
public class UnreachableTargetClient {

    public static void main(String[] args) {
        // Deliberately connect to an unreachable port (e.g. closed port 65534)
        int unreachablePort = 65534;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", unreachablePort), 500);
            System.out.println("CONNECTED_UNEXPECTEDLY");
        } catch (Exception e) {
            System.err.println("Client failed to connect to port " + unreachablePort + ": " + e.getMessage());
            System.exit(42);
        }
    }
}
