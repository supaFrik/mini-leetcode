package jsd.toiyeujava.mini_leetcode.submission.dto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public record SubmissionArchiveResult(
        byte[] zipBytes,
        List<String> entryPaths,
        long totalUncompressedSize,
        int entryCount
) {
    public InputStream getInputStream() {
        return new ByteArrayInputStream(zipBytes != null ? zipBytes : new byte[0]);
    }
}
