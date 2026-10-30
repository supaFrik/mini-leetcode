package jsd.toiyeujava.mini_leetcode.testcase.dto;

import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;

import java.time.LocalDateTime;

public record TestCaseResponse(
        Long id,
        Long problemId,
        String inputFileUri,
        String expectedOutputUri,
        Boolean isHidden,
        Integer orderIndex,
        Long fileSizeBytes,
        LocalDateTime createdAt,
        String input,
        String expectedOutput
) {
    public static TestCaseResponse fromEntity(TestCase tc) {
        return fromEntity(tc, null, null);
    }

    public static TestCaseResponse fromEntity(TestCase tc, String input, String expectedOutput) {
        return new TestCaseResponse(
                tc.getId(),
                tc.getProblem() != null ? tc.getProblem().getId() : null,
                tc.getInputFileUri(),
                tc.getExpectedOutputUri(),
                tc.getIsHidden(),
                tc.getOrderIndex(),
                tc.getFileSizeBytes(),
                tc.getCreatedAt(),
                input,
                expectedOutput
        );
    }
}
