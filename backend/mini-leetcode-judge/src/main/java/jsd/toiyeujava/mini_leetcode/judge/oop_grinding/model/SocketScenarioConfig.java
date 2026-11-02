package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SocketScenarioConfig {
    private String scenarioName;
    @Builder.Default
    private SocketRole role = SocketRole.CLIENT;
    private SocketAssessmentMode mode;
    private String targetClass;
    private String targetMainClass;
    @Builder.Default
    private int concurrentClients = 1;
    @Builder.Default
    private int timeoutMs = 3000;
    @Builder.Default
    private int connectTimeoutMs = 2000;
    @Builder.Default
    private List<SocketStep> steps = new ArrayList<>();
    private String requestPayload;
    private String expectedResponsePayload;
    @Builder.Default
    private List<String> extraArgs = new ArrayList<>();
    private String serverArgs;
    @Builder.Default
    private Double weight = 1.0;
    @Builder.Default
    private boolean expectResourceCleanup = true;
    @Builder.Default
    private boolean lineDelimited = true;
    private String shutdownPayload;

    public String getEffectiveTargetClass() {
        if (targetClass != null && !targetClass.isBlank()) {
            return targetClass;
        }
        return targetMainClass;
    }

    public void setTargetMainClass(String targetMainClass) {
        this.targetMainClass = targetMainClass;
        if (this.targetClass == null || this.targetClass.isBlank()) {
            this.targetClass = targetMainClass;
        }
    }

    public List<SocketStep> resolveSteps() {
        if (steps != null && !steps.isEmpty()) {
            return steps;
        }
        List<SocketStep> resolved = new ArrayList<>();
        if (requestPayload != null || expectedResponsePayload != null) {
            if (role == SocketRole.CLIENT) {
                resolved.add(SocketStep.builder()
                        .expectedPayload(requestPayload)
                        .sendPayload(expectedResponsePayload)
                        .receiveFirst(true)
                        .build());
            } else {
                resolved.add(SocketStep.builder()
                        .sendPayload(requestPayload)
                        .expectedPayload(expectedResponsePayload)
                        .receiveFirst(false)
                        .build());
            }
        }
        return resolved;
    }
}
