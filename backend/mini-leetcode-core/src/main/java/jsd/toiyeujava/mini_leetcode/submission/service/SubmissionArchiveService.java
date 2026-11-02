package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;

import java.io.InputStream;
import java.util.List;

public interface SubmissionArchiveService {

    /**
     * Packages a list of in-memory files into a standardized, sanitized ZIP archive.
     */
    SubmissionArchiveResult packageFiles(List<SubmissionFileDto> files);

    /**
     * Reads an incoming raw ZIP stream (e.g. multipart upload), defends against Zip Slip / Zip Bomb,
     * strips metadata, and repackages into a clean, reproducible ZIP archive.
     */
    SubmissionArchiveResult sanitizeAndRepackageZip(InputStream rawZipStream);

    /**
     * Alias for sanitizeAndRepackageZip.
     */
    default SubmissionArchiveResult validateAndSanitizeZip(InputStream rawZipStream) {
        return sanitizeAndRepackageZip(rawZipStream);
    }

    /**
     * Parses delimited text code ("// === FILE: ... ===") or single class fallback,
     * and packages the resulting files into a clean ZIP archive.
     */
    SubmissionArchiveResult packageDelimitedCode(String sourceCode, SubmissionLanguage language);

    /**
     * Default Java overload for delimited source packaging.
     */
    default SubmissionArchiveResult packageDelimited(String sourceCode) {
        return packageDelimitedCode(sourceCode, SubmissionLanguage.JAVA);
    }

    /**
     * Universal dispatcher that inspects incoming multi-channel arguments and packages into a ZIP archive.
     */
    SubmissionArchiveResult packageAndValidate(
            List<SubmissionFileDto> files,
            InputStream rawZipStream,
            String delimitedCode,
            SubmissionLanguage language
    );

    /**
     * Generates an offline starter project ZIP containing starter code templates and a generated README.md.
     */
    byte[] generateStarterZip(String problemSlug, String problemTitle, String description, List<SubmissionFileDto> templates);

    /**
     * Convenience overload for starter project ZIP generation.
     */
    default byte[] generateStarterZip(String problemSlug, String description, List<SubmissionFileDto> templates) {
        return generateStarterZip(problemSlug, problemSlug, description, templates);
    }

    /**
     * Generates starter project ZIP directly from Problem entity.
     */
    byte[] generateStarterZip(Problem problem, List<SubmissionFileDto> starterTemplates);

    /**
     * Uploads the packaged ZIP archive to MinIO "submissions" bucket.
     * Returns the full storage URI: "minio://submissions/submissions/{problemId}/{userId}/{uuid}.zip"
     */
    String uploadSubmissionArchive(Long problemId, Long userId, byte[] zipBytes);
}
