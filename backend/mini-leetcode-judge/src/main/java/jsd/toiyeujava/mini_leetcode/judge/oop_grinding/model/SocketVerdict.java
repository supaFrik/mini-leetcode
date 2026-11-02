package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

public enum SocketVerdict {
    SUCCESS(100.0),
    RESOURCE_LEAK(50.0),
    CONNECTION_FAILURE(0.0),
    PROTOCOL_MISMATCH(0.0),
    SCENARIO_TIMEOUT(0.0);

    private final double baseScore;

    SocketVerdict(double baseScore) {
        this.baseScore = baseScore;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public double getScore() {
        return baseScore;
    }
}
