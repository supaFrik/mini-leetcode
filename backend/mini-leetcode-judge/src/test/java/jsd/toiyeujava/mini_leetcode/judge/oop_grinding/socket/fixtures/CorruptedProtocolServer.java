package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Fixture: Server that sends corrupted protocol payloads:
 * - Garbage binary
 * - Truncated data
 * - Mismatched string payloads
 * - Unexpected stream closures without newline
 */
public class CorruptedProtocolServer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CorruptedProtocolServer <port> [mode]");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String mode = args.length >= 2 ? args[1] : "GARBAGE_BINARY";

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("CORRUPTED_SERVER_READY:" + serverSocket.getLocalPort());
            System.out.flush();

            while (!serverSocket.isClosed()) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (Exception e) {
                    break;
                }

                try (clientSocket;
                     InputStream in = clientSocket.getInputStream();
                     OutputStream out = clientSocket.getOutputStream()) {

                    // Read request line if present
                    byte[] buf = new byte[1024];
                    int read = in.read(buf);

                    switch (mode.toUpperCase()) {
                        case "GARBAGE_BINARY":
                            // Corrupted non-printable binary bytes with null bytes and invalid UTF-8 sequences
                            byte[] garbage = new byte[] {
                                    0x00, 0x1F, (byte) 0x8B, 0x08, 0x00, (byte) 0xFF, (byte) 0xFE, 0x07, '\n'
                            };
                            out.write(garbage);
                            out.flush();
                            break;

                        case "TRUNCATED":
                            // Truncated payload without terminating newline, followed by immediate close
                            out.write("TRUNC".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            break;

                        case "MISMATCH_STRING":
                            out.write("WRONG_EXPECTED_STRING_XYZ\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            break;

                        default:
                            out.write("UNKNOWN_CORRUPTION\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("CorruptedProtocolServer error: " + e.getMessage());
            System.exit(2);
        }
    }
}
