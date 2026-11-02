package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

/**
 * Fixture: Server that remains alive and running but NEVER binds or listens on the assigned port.
 * Used to verify the harness polling loop exhausts all attempts (10 x 200ms = 2000ms)
 * and returns CONNECTION_FAILURE with score 0.0 without hanging.
 */
public class SilentUnboundServer {

    public static void main(String[] args) {
        System.out.println("SILENT_SERVER_STARTED_WITHOUT_BINDING");
        System.out.flush();

        try {
            // Sleep for 10 seconds without opening ServerSocket
            Thread.sleep(10_000L);
        } catch (InterruptedException ignored) {
        }
    }
}
