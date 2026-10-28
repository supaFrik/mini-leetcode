package jsd.toiyeujava.mini_leetcode.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

public record SubmitCodeRequest(
        @NotNull(message = "Language is required")
        SubmissionLanguage language,

        @NotBlank(message = "Code must not be blank")
        @Size(max = 65536, message = "Code must not exceed 64KB")
        String code,

        Long contestId
) {
    public SubmitCodeRequest(SubmissionLanguage language, String code) {
        this(language, code, null);
    }
}
