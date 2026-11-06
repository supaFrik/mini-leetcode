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
        String expectedOutput,
        /** {@code true} khi {@code input} chỉ là phần đầu của file (file lớn hơn giới hạn xem trước). */
        boolean inputTruncated,
        boolean expectedOutputTruncated
) {
    public static TestCaseResponse fromEntity(TestCase tc) {
        return fromEntity(tc, null, null);
    }

    public static TestCaseResponse fromEntity(TestCase tc, String input, String expectedOutput) {
        return fromEntity(tc, input, expectedOutput, false, false);
    }

    public static TestCaseResponse fromEntity(TestCase tc, String input, String expectedOutput,
                                              boolean inputTruncated, boolean expectedOutputTruncated) {
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
                expectedOutput,
                inputTruncated,
                expectedOutputTruncated
        );
    }
}
