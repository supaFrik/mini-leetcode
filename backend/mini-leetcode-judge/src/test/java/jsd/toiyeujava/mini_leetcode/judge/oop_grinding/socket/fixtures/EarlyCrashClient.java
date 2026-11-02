package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

public class EarlyCrashClient {
    public static void main(String[] args) {
        int exitCode = 137;
        if (args.length >= 3) {
            try {
                exitCode = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        System.err.println("EarlyCrashClient fatal crash before connection with exit code " + exitCode);
        if (exitCode == 999) {
            throw new RuntimeException("Uncaught early runtime exception in client main");
        }
        System.exit(exitCode);
    }
}
