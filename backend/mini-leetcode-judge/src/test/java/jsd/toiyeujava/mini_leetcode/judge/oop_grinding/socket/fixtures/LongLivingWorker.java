package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures;

public class LongLivingWorker {
    public static void main(String[] args) {
        System.out.println("LongLivingWorker PID " + ProcessHandle.current().pid() + " running...");
        System.out.flush();
        try {
            Thread.sleep(60_000);
        } catch (InterruptedException ignored) {
        }
        System.out.println("LongLivingWorker finished.");
    }
}
