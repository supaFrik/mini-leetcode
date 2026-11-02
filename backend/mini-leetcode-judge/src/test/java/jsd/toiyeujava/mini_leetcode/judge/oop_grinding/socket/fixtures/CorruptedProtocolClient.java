package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Fixture: Client that sends corrupted protocol payloads:
 * - Garbage binary
 * - Mismatched string payloads
 * - Truncated data
 */
public class CorruptedProtocolClient {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CorruptedProtocolClient [host] <port> [mode]");
            System.exit(1);
        }

        String host = args.length >= 2 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.parseInt(args[0]);
        String mode = args.length >= 3 ? args[2] : "GARBAGE_BINARY";

        try (Socket socket = new Socket(host, port);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            switch (mode.toUpperCase()) {
                case "GARBAGE_BINARY":
                    byte[] garbage = new byte[] {
                            0x00, 0x1F, (byte) 0x8B, 0x08, 0x00, (byte) 0xFF, (byte) 0xFE, 0x07, '\n'
                    };
                    out.write(garbage);
                    out.flush();
                    break;

                case "TRUNCATED":
                    out.write("PARTIAL_DATA".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    break;

                case "MISMATCH_STRING":
                default:
                    out.write("COMPLETELY_WRONG_CLIENT_PAYLOAD\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    break;
            }

            // Briefly allow server to read before client terminates
            Thread.sleep(200);

        } catch (Exception e) {
            System.err.println("CorruptedProtocolClient error: " + e.getMessage());
            System.exit(2);
        }
    }
}
