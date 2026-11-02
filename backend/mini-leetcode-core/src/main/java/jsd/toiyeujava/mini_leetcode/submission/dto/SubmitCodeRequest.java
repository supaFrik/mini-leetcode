package jsd.toiyeujava.mini_leetcode.submission.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

import java.util.List;

public record SubmitCodeRequest(
        Long problemId,

        @NotNull(message = "Language is required")
        SubmissionLanguage language,

        String code,

        List<SubmissionFileDto> files,

        Long contestId,

        Boolean isQuickRun,

        @JsonIgnore
        byte[] archiveBytes,

        @JsonIgnore
        String codeStorageUri
) {
    public SubmitCodeRequest {
        if (files == null) {
            files = List.of();
        }
        if (isQuickRun == null) {
            isQuickRun = false;
        }
    }

    // Existing 6-argument constructor for backwards compatibility
    public SubmitCodeRequest(
            Long problemId,
            SubmissionLanguage language,
            String code,
            List<SubmissionFileDto> files,
            Long contestId,
            Boolean isQuickRun
    ) {
        this(problemId, language, code, files, contestId, isQuickRun, null, null);
    }

    // 7-argument constructor for multipart zip uploads
    public SubmitCodeRequest(
            Long problemId,
            SubmissionLanguage language,
            String code,
            List<SubmissionFileDto> files,
            Long contestId,
            Boolean isQuickRun,
            byte[] archiveBytes
    ) {
        this(problemId, language, code, files, contestId, isQuickRun, archiveBytes, null);
    }

    // Existing 2-argument convenience constructor
    public SubmitCodeRequest(SubmissionLanguage language, String code) {
        this(null, language, code, List.of(), null, false, null, null);
    }

    // Existing 3-argument convenience constructor
    public SubmitCodeRequest(SubmissionLanguage language, String code, Long contestId) {
        this(null, language, code, List.of(), contestId, false, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Code must not be blank")
    public boolean isValidPayload() {
        boolean hasFiles = files != null && !files.isEmpty();
        boolean hasCode = code != null && !code.isBlank();
        boolean hasArchive = archiveBytes != null && archiveBytes.length > 0;
        boolean hasStorageUri = codeStorageUri != null && !codeStorageUri.isBlank();
        return hasFiles || hasCode || hasArchive || hasStorageUri;
    }
}
