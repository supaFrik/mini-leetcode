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
public class OopMethodConstraint {
    private String name;
    private String returnType;
    @Builder.Default
    private List<String> parameterTypes = new ArrayList<>();
    @Builder.Default
    private List<String> requiredModifiers = new ArrayList<>();
    @Builder.Default
    private List<String> forbiddenModifiers = new ArrayList<>();
    @Builder.Default
    private boolean ignoreSynthetic = true;
}
