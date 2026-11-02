package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocketPhaseReport {
    private boolean passed;
    private Double score;
    @Builder.Default
    private Double weight = 0.30;
    private Double weightedScore;
    @Builder.Default
    private List<ExecutionReport.SocketScenarioResult> scenarios = new ArrayList<>();
    private String summary;

    public ExecutionReport.SocketIntegrationPhase toSocketIntegrationPhase() {
        return ExecutionReport.SocketIntegrationPhase.builder()
                .score(this.score)
                .weight(this.weight)
                .weightedScore(this.weightedScore)
                .summary(this.summary)
                .scenarios(this.scenarios != null ? this.scenarios : new ArrayList<>())
                .build();
    }
}
