package jsd.toiyeujava.mini_leetcode.storage.service;

import java.io.InputStream;
import java.util.UUID;

public interface StorageService {

    // =========================================================================
    // Multi-Bucket Operations (Primary API)
    // =========================================================================

    /**
     * Uploads an object into a specified bucket with streaming and content-type metadata.
     *
     * @param bucketName  the target bucket (e.g. "submissions" or "testcases")
     * @param objectName  the object key path within the bucket
     * @param inputStream the data stream
     * @param size        the exact size in bytes (-1 if unknown)
     * @param contentType MIME type (e.g. "application/zip", "text/plain")
     * @return fully qualified URI in format "minio://{bucketName}/{objectName}"
     */
    String uploadFile(String bucketName, String objectName, InputStream inputStream, long size, String contentType);

    /**
     * Downloads an object from a specified bucket. Caller MUST close the returned InputStream.
     */
    InputStream downloadFile(String bucketName, String objectName);

    /**
     * Deletes an object from a specified bucket.
     */
    void deleteFile(String bucketName, String objectName);

    /**
     * Checks if an object exists in a specified bucket.
     */
    boolean fileExists(String bucketName, String objectName);

    /**
     * Returns the configured default bucket name (typically "testcases").
     */
    String getBucketName();

    /**
     * Returns the configured submissions bucket name (typically "submissions").
     */
    default String getSubmissionsBucketName() {
        return "submissions";
    }

    // =========================================================================
    // Backward-Compatible Single-Bucket Overloads
    // =========================================================================

    default String uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        return uploadFile(getBucketName(), objectName, inputStream, size, contentType);
    }

    default InputStream downloadFile(String objectName) {
        String bucket = extractBucketName(objectName);
        if (bucket != null && !bucket.isBlank()) {
            return downloadFile(bucket, extractObjectName(objectName));
        }
        return downloadFile(getBucketName(), extractObjectName(objectName));
    }

    default void deleteFile(String objectName) {
        String bucket = extractBucketName(objectName);
        if (bucket != null && !bucket.isBlank()) {
            deleteFile(bucket, extractObjectName(objectName));
            return;
        }
        deleteFile(getBucketName(), extractObjectName(objectName));
    }

    default boolean fileExists(String objectName) {
        String bucket = extractBucketName(objectName);
        if (bucket != null && !bucket.isBlank()) {
            return fileExists(bucket, extractObjectName(objectName));
        }
        return fileExists(getBucketName(), extractObjectName(objectName));
    }

    // =========================================================================
    // URI Resolution & Parsing Utilities
    // =========================================================================

    /**
     * Extracts the bucket name from a URI of the form "minio://{bucketName}/{objectName}".
     * Returns null if the URI does not contain a scheme/bucket prefix.
     */
    default String extractBucketName(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        int protocolIdx = uri.indexOf("://");
        if (protocolIdx != -1) {
            String path = uri.substring(protocolIdx + 3);
            int slashIdx = path.indexOf('/');
            if (slashIdx != -1) {
                return path.substring(0, slashIdx);
            }
        }
        return null;
    }

    /**
     * Extracts the object key path from a URI, stripping "minio://{bucketName}/".
     * If no protocol is present, returns the trimmed string as-is.
     */
    default String extractObjectName(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String objectName = uri;
        int protocolIdx = uri.indexOf("://");
        if (protocolIdx != -1) {
            String path = uri.substring(protocolIdx + 3);
            int slashIdx = path.indexOf('/');
            objectName = slashIdx != -1 ? path.substring(slashIdx + 1) : path;
        }
        return objectName;
    }

    /**
     * Downloads an object seamlessly based on its URI.
     * Handles both "minio://submissions/..." and "minio://testcases/..." seamlessly.
     */
    default InputStream downloadFileByUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String bucket = extractBucketName(uri);
        String objectName = extractObjectName(uri);
        if (bucket != null && !bucket.isBlank()) {
            return downloadFile(bucket, objectName);
        }
        return downloadFile(objectName);
    }

    /**
     * Deletes an object by its URI, routing to the correct bucket automatically.
     */
    default void deleteFileByUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        String bucket = extractBucketName(uri);
        String objectName = extractObjectName(uri);
        if (!objectName.isBlank()) {
            try {
                if (bucket != null && !bucket.isBlank()) {
                    deleteFile(bucket, objectName);
                } else {
                    deleteFile(objectName);
                }
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }

    // =========================================================================
    // Key Generation Utilities
    // =========================================================================

    default String generateSubmissionKey(Long problemId, Long userId, String extension) {
        String ext = (extension != null && !extension.isBlank())
                ? (extension.startsWith(".") ? extension : "." + extension)
                : ".zip";
        return String.format("submissions/%d/%d/%s%s", problemId, userId, UUID.randomUUID(), ext);
    }

    default String generateSubmissionZipKey(Long problemId, Long userId) {
        return generateSubmissionKey(problemId, userId, ".zip");
    }

    default String uploadSubmission(Long problemId, Long userId, InputStream inputStream, long size) {
        String key = generateSubmissionZipKey(problemId, userId);
        return uploadFile(getSubmissionsBucketName(), key, inputStream, size, "application/zip");
    }
}
