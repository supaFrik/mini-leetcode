package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Score Aggregator for OOP Grinding evaluation pipeline.
 * Computes normalized scores, performs dynamic weight re-balancing,
 * maps final verdicts, and serializes score breakdown JSON matching PROJECT.md § 4.4.
 */
@Slf4j
@Component
public class OopScoreCalculator {

    public static final double DEFAULT_OOP_WEIGHT_WITH_SOCKET = 0.30;
    public static final double DEFAULT_UNIT_WEIGHT_WITH_SOCKET = 0.40;
    public static final double DEFAULT_SOCKET_WEIGHT = 0.30;

    public static final double DEFAULT_OOP_WEIGHT_NO_SOCKET = 0.40;
    public static final double DEFAULT_UNIT_WEIGHT_NO_SOCKET = 0.60;

    private final ObjectMapper objectMapper;

    @Autowired
    public OopScoreCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public OopScoreCalculator() {
        this(new ObjectMapper());
    }

    public record Weights(double oop, double unit, double socket) {}

    @Value
    @Builder
    public static class ScoreResult {
        double totalScore;
        String verdict;
        SubmissionVerdict submissionVerdict;
        double oopWeight;
        double oopWeightedScore;
        double unitWeight;
        double unitWeightedScore;
        double socketWeight;
        double socketWeightedScore;
    }

    /**
     * Resolves dynamic weights based on harness configuration.
     * With Socket: 30% OOP + 40% Unit + 30% Socket.
     * Without Socket: 40% OOP + 60% Unit.
     * Edge case (single phase active): normalized to 1.00.
     */
    public Weights resolveWeights(HarnessConfig config) {
        if (config == null) {
            return new Weights(DEFAULT_OOP_WEIGHT_NO_SOCKET, DEFAULT_UNIT_WEIGHT_NO_SOCKET, 0.0);
        }

        boolean oopActive = config.getOopInspection() != null && config.getOopInspection().isEnabled();
        boolean unitActive = config.getUnitTest() != null && config.getUnitTest().isEnabled();
        boolean socketActive = config.getSocketIntegration() != null
                && config.getSocketIntegration().isEnabled()
                && config.getSocketIntegration().getScenarios() != null
                && !config.getSocketIntegration().getScenarios().isEmpty()
                && config.getSocketIntegration().getWeight() > 0.0;

        if (oopActive && unitActive && socketActive) {
            return new Weights(DEFAULT_OOP_WEIGHT_WITH_SOCKET, DEFAULT_UNIT_WEIGHT_WITH_SOCKET, DEFAULT_SOCKET_WEIGHT);
        }
        if (oopActive && unitActive) {
            return new Weights(DEFAULT_OOP_WEIGHT_NO_SOCKET, DEFAULT_UNIT_WEIGHT_NO_SOCKET, 0.0);
        }
        if (oopActive && socketActive) {
            return new Weights(0.50, 0.0, 0.50);
        }
        if (unitActive && socketActive) {
            return new Weights(0.0, 0.57, 0.43);
        }
        if (oopActive) {
            return new Weights(1.00, 0.0, 0.0);
        }
        if (unitActive) {
            return new Weights(0.0, 1.00, 0.0);
        }
        if (socketActive) {
            return new Weights(0.0, 0.0, 1.00);
        }

        return new Weights(0.0, 0.0, 0.0);
    }

    /**
     * Calculates normalized total score and phase breakdown.
     */
    public ScoreResult calculate(Double oopScore, Double unitScore, Double socketScore, boolean hasSocket) {
        double safeOop = oopScore != null ? oopScore : 0.0;
        double safeUnit = unitScore != null ? unitScore : 0.0;
        double safeSocket = socketScore != null ? socketScore : 0.0;

        double wOop = hasSocket ? DEFAULT_OOP_WEIGHT_WITH_SOCKET : DEFAULT_OOP_WEIGHT_NO_SOCKET;
        double wUnit = hasSocket ? DEFAULT_UNIT_WEIGHT_WITH_SOCKET : DEFAULT_UNIT_WEIGHT_NO_SOCKET;
        double wSocket = hasSocket ? DEFAULT_SOCKET_WEIGHT : 0.0;

        return calculateWithWeights(safeOop, safeUnit, safeSocket, new Weights(wOop, wUnit, wSocket));
    }

    /**
     * Calculates total score with explicit weights.
     */
    public ScoreResult calculateWithWeights(double safeOop, double safeUnit, double safeSocket, Weights weights) {
        double weightedOop = round1(safeOop * weights.oop());
        double weightedUnit = round1(safeUnit * weights.unit());
        double weightedSocket = round1(safeSocket * weights.socket());

        double total = round1(weightedOop + weightedUnit + weightedSocket);
        total = Math.max(0.0, Math.min(100.0, total));

        SubmissionVerdict submissionVerdict = (total >= 100.0 || Math.abs(total - 100.0) < 1e-4)
                ? SubmissionVerdict.AC
                : SubmissionVerdict.WA;
        String verdictStr = submissionVerdict.name();

        return ScoreResult.builder()
                .totalScore(total)
                .verdict(verdictStr)
                .submissionVerdict(submissionVerdict)
                .oopWeight(weights.oop())
                .oopWeightedScore(weightedOop)
                .unitWeight(weights.unit())
                .unitWeightedScore(weightedUnit)
                .socketWeight(weights.socket())
                .socketWeightedScore(weightedSocket)
                .build();
    }

    /**
     * Aggregates phase results in an ExecutionReport and enriches with normalized scores.
     */
    public ExecutionReport aggregateAndEnrich(ExecutionReport report, HarnessConfig config) {
        if (report == null) {
            return null;
        }

        // Non-normal phases (timeouts, compilation errors, OOM, crashes) keep score 0.0
        String phase = report.getExecutionPhase();
        if (phase != null && !"COMPLETED".equalsIgnoreCase(phase)) {
            report.setTotalScore(0.0);
            report.setVerdict(resolveVerdict(0.0, phase).name());
            return report;
        }

        if (report.getPhases() == null) {
            if (report.getTotalScore() == null) {
                report.setTotalScore(0.0);
            }
            if (report.getVerdict() == null) {
                report.setVerdict(resolveVerdict(report.getTotalScore(), report.getExecutionPhase()).name());
            }
            return report;
        }

        Weights weights = resolveWeights(config);

        Double oopScore = 0.0;
        Double unitScore = 0.0;
        Double socketScore = 0.0;

        if (report.getPhases() != null) {
            if (report.getPhases().getOopInspection() != null && report.getPhases().getOopInspection().getScore() != null) {
                oopScore = report.getPhases().getOopInspection().getScore();
            }
            if (report.getPhases().getUnitTest() != null && report.getPhases().getUnitTest().getScore() != null) {
                unitScore = report.getPhases().getUnitTest().getScore();
            }
            if (report.getPhases().getSocketIntegration() != null && report.getPhases().getSocketIntegration().getScore() != null) {
                socketScore = report.getPhases().getSocketIntegration().getScore();
            }
        }

        ScoreResult result = calculateWithWeights(oopScore, unitScore, socketScore, weights);

        if (report.getPhases() != null) {
            if (report.getPhases().getOopInspection() != null) {
                report.getPhases().getOopInspection().setWeight(result.getOopWeight());
                report.getPhases().getOopInspection().setWeightedScore(result.getOopWeightedScore());
            }
            if (report.getPhases().getUnitTest() != null) {
                report.getPhases().getUnitTest().setWeight(result.getUnitWeight());
                report.getPhases().getUnitTest().setWeightedScore(result.getUnitWeightedScore());
            }
            if (report.getPhases().getSocketIntegration() != null) {
                report.getPhases().getSocketIntegration().setWeight(result.getSocketWeight());
                report.getPhases().getSocketIntegration().setWeightedScore(result.getSocketWeightedScore());
            }
        }

        report.setTotalScore(result.getTotalScore());
        report.setVerdict(result.getVerdict());
        if (report.getExecutionPhase() == null) {
            report.setExecutionPhase("COMPLETED");
        }

        return report;
    }

    /**
     * Overload for aggregateAndEnrich given hasSocket boolean.
     */
    public ExecutionReport aggregateAndEnrich(ExecutionReport report, boolean hasSocket) {
        Weights weights = new Weights(
                hasSocket ? DEFAULT_OOP_WEIGHT_WITH_SOCKET : DEFAULT_OOP_WEIGHT_NO_SOCKET,
                hasSocket ? DEFAULT_UNIT_WEIGHT_WITH_SOCKET : DEFAULT_UNIT_WEIGHT_NO_SOCKET,
                hasSocket ? DEFAULT_SOCKET_WEIGHT : 0.0
        );

        if (report == null) return null;

        String phase = report.getExecutionPhase();
        if (phase != null && !"COMPLETED".equalsIgnoreCase(phase)) {
            report.setTotalScore(0.0);
            report.setVerdict(resolveVerdict(0.0, phase).name());
            return report;
        }

        if (report.getPhases() == null) {
            if (report.getTotalScore() == null) {
                report.setTotalScore(0.0);
            }
            if (report.getVerdict() == null) {
                report.setVerdict(resolveVerdict(report.getTotalScore(), report.getExecutionPhase()).name());
            }
            return report;
        }

        Double oopScore = (report.getPhases() != null && report.getPhases().getOopInspection() != null && report.getPhases().getOopInspection().getScore() != null)
                ? report.getPhases().getOopInspection().getScore() : 0.0;
        Double unitScore = (report.getPhases() != null && report.getPhases().getUnitTest() != null && report.getPhases().getUnitTest().getScore() != null)
                ? report.getPhases().getUnitTest().getScore() : 0.0;
        Double socketScore = (report.getPhases() != null && report.getPhases().getSocketIntegration() != null && report.getPhases().getSocketIntegration().getScore() != null)
                ? report.getPhases().getSocketIntegration().getScore() : 0.0;

        ScoreResult result = calculateWithWeights(oopScore, unitScore, socketScore, weights);

        if (report.getPhases() != null) {
            if (report.getPhases().getOopInspection() != null) {
                report.getPhases().getOopInspection().setWeight(result.getOopWeight());
                report.getPhases().getOopInspection().setWeightedScore(result.getOopWeightedScore());
            }
            if (report.getPhases().getUnitTest() != null) {
                report.getPhases().getUnitTest().setWeight(result.getUnitWeight());
                report.getPhases().getUnitTest().setWeightedScore(result.getUnitWeightedScore());
            }
            if (report.getPhases().getSocketIntegration() != null) {
                report.getPhases().getSocketIntegration().setWeight(result.getSocketWeight());
                report.getPhases().getSocketIntegration().setWeightedScore(result.getSocketWeightedScore());
            }
        }

        report.setTotalScore(result.getTotalScore());
        report.setVerdict(result.getVerdict());
        if (report.getExecutionPhase() == null) {
            report.setExecutionPhase("COMPLETED");
        }
        return report;
    }

    /**
     * Resolves submission verdict based on total score and execution phase.
     */
    public SubmissionVerdict resolveVerdict(Double totalScore, String executionPhase) {
        if (executionPhase == null) {
            executionPhase = "COMPLETED";
        }
        return switch (executionPhase.toUpperCase(Locale.ROOT)) {
            case "COMPILATION_FAILED" -> SubmissionVerdict.CE;
            case "TIMEOUT" -> SubmissionVerdict.TLE;
            case "OUT_OF_MEMORY" -> SubmissionVerdict.MLE;
            case "EXECUTION_FAILED" -> SubmissionVerdict.RE;
            case "COMPLETED" -> (totalScore != null && (totalScore >= 100.0 || Math.abs(totalScore - 100.0) < 1e-4))
                    ? SubmissionVerdict.AC
                    : SubmissionVerdict.WA;
            default -> (totalScore != null && (totalScore >= 100.0 || Math.abs(totalScore - 100.0) < 1e-4))
                    ? SubmissionVerdict.AC
                    : SubmissionVerdict.WA;
        };
    }

    /**
     * Maps string verdict representation to SubmissionVerdict enum.
     */
    public SubmissionVerdict mapVerdict(String verdictStr) {
        if (verdictStr == null) return SubmissionVerdict.WA;
        return switch (verdictStr.toUpperCase(Locale.ROOT)) {
            case "AC" -> SubmissionVerdict.AC;
            case "CE" -> SubmissionVerdict.CE;
            case "RE" -> SubmissionVerdict.RE;
            case "TLE" -> SubmissionVerdict.TLE;
            case "MLE" -> SubmissionVerdict.MLE;
            default -> SubmissionVerdict.WA;
        };
    }

    /**
     * Formats ExecutionReport to score_breakdown JSON matching PROJECT.md § 4.4.
     */
    public String formatScoreBreakdownJson(ExecutionReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("Failed to serialize ExecutionReport into JSON", e);
            double totalScore = report != null && report.getTotalScore() != null ? report.getTotalScore() : 0.0;
            String verdict = report != null && report.getVerdict() != null ? report.getVerdict() : "WA";
            String phase = report != null && report.getExecutionPhase() != null ? report.getExecutionPhase() : "COMPLETED";
            return "{\"totalScore\":" + totalScore + ",\"verdict\":\"" + verdict + "\",\"executionPhase\":\"" + phase + "\"}";
        }
    }

    /**
     * Half-up rounding to 1 decimal place.
     */
    public static double round1(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
