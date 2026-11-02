package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SourcePathValidator {

    public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 20 * 1024 * 1024L; // 20 MB
    public static final long MAX_SINGLE_FILE_SIZE = 20 * 1024 * 1024L;        // 20 MB
    public static final int MAX_ENTRIES = 100;

    public static final List<String> FORBIDDEN_EXTENSIONS = List.of(
            ".exe", ".dll", ".so", ".sh", ".bat", ".cmd", ".jar", ".class"
    );

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private SourcePathValidator() {
    }

    public static String sanitizeAndValidatePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidSubmissionPathException("Archive entry path cannot be null or empty");
        }

        if (rawPath.contains("\0")) {
            throw new InvalidSubmissionPathException("Illegal null byte in archive entry path: " + rawPath);
        }

        // Standardize slashes
        String normalized = rawPath.replace('\\', '/').trim();

        // Check for absolute, drive letter, UNC, or device namespace paths
        if (rawPath.startsWith("/") || rawPath.startsWith("\\")
                || normalized.startsWith("/") || normalized.startsWith("\\")
                || normalized.matches("^[a-zA-Z]:.*")
                || normalized.startsWith("//") || rawPath.startsWith("\\\\")
                || rawPath.contains("\\\\?\\") || rawPath.contains("\\\\.\\")
                || normalized.contains("//?/") || normalized.contains("//./")) {
            throw new InvalidSubmissionPathException("Illegal absolute or UNC path in archive entry: " + rawPath);
        }

        String[] parts = normalized.split("/");
        List<String> validSegments = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || ".".equals(trimmed)) {
                continue;
            }
            if ("..".equals(trimmed)) {
                throw new InvalidSubmissionPathException("Zip Slip path traversal (..) detected in entry: " + rawPath);
            }

            // Check Windows device reserved names
            String baseName = trimmed;
            int dotIdx = baseName.indexOf('.');
            if (dotIdx != -1) {
                baseName = baseName.substring(0, dotIdx);
            }
            if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase())) {
                throw new InvalidSubmissionPathException("Reserved device name in archive entry: " + rawPath);
            }

            validSegments.add(trimmed);
        }

        if (validSegments.isEmpty()) {
            throw new InvalidSubmissionPathException("Archive entry path resolves to empty or root directory: " + rawPath);
        }

        String sanitized = String.join("/", validSegments);

        String lower = sanitized.toLowerCase();
        for (String forbidden : FORBIDDEN_EXTENSIONS) {
            if (lower.endsWith(forbidden)) {
                throw new InvalidSubmissionPathException("Forbidden executable file type in archive: " + rawPath);
            }
        }

        return sanitized;
    }

    public static boolean isIgnoredMetadata(String rawName) {
        if (rawName == null) {
            return false;
        }
        String normalized = rawName.replace('\\', '/');
        return normalized.startsWith("__MACOSX/") || normalized.contains("/__MACOSX/")
                || normalized.endsWith(".DS_Store") || normalized.equals(".DS_Store");
    }

    public static void validateEntryCount(int count) {
        if (count > MAX_ENTRIES) {
            throw new InvalidSubmissionPathException("Submission exceeds maximum entry count of " + MAX_ENTRIES);
        }
    }

    public static void validateUncompressedSize(long size) {
        if (size > MAX_TOTAL_UNCOMPRESSED_SIZE) {
            throw new PayloadTooLargeException("Submission uncompressed size exceeds maximum allowed limit of 20MB");
        }
    }

    public static void validateSingleFileSize(String path, long size) {
        if (size > MAX_SINGLE_FILE_SIZE) {
            throw new PayloadTooLargeException("File [" + path + "] exceeds max size limit of 20MB");
        }
    }
}
