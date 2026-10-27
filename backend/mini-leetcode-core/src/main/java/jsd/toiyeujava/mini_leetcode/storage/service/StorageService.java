package jsd.toiyeujava.mini_leetcode.storage.service;

import java.io.InputStream;

public interface StorageService {
    String uploadFile(String objectName, InputStream inputStream, long size, String contentType);
    InputStream downloadFile(String objectName);
    void deleteFile(String objectName);
    boolean fileExists(String objectName);
    String getBucketName();

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

    default InputStream downloadFileByUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String objectName = extractObjectName(uri);
        return downloadFile(objectName);
    }

    default void deleteFileByUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        String objectName = extractObjectName(uri);
        if (!objectName.isBlank()) {
            try {
                deleteFile(objectName);
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }
}
