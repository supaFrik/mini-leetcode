package jsd.toiyeujava.mini_leetcode.submission.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmissionFileDto(
        @NotBlank(message = "File path must not be blank")
        String path,

        String content
) {
    public SubmissionFileDto {
        if (content == null) {
            content = "";
        }
    }
}
