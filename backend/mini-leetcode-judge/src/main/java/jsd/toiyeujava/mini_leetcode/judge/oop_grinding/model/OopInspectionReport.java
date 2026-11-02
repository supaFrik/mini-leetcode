package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class OopInspectionReport {
    private boolean passed;
    private double score;
    @Builder.Default
    private double weight = 0.30;
    private double weightedScore;
    @Builder.Default
    private List<OopRuleResult> rules = new ArrayList<>();
    private String summary;
}
