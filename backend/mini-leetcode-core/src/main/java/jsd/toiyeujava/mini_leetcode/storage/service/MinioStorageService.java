package jsd.toiyeujava.mini_leetcode.storage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String defaultBucketName;
    private final String submissionsBucketName;

    // Cache to prevent redundant bucketExists network round-trips under high concurrency
    private final Set<String> initializedBuckets = ConcurrentHashMap.newKeySet();

    @Autowired
    public MinioStorageService(
            MinioClient minioClient,
            @Value("${app.minio.bucket-name:testcases}") String defaultBucketName,
            @Value("${app.minio.submissions-bucket-name:submissions}") String submissionsBucketName) {
        this.minioClient = minioClient;
        this.defaultBucketName = defaultBucketName != null && !defaultBucketName.isBlank() ? defaultBucketName : "testcases";
        this.submissionsBucketName = submissionsBucketName != null && !submissionsBucketName.isBlank() ? submissionsBucketName : "submissions";
    }

    // Overloaded constructor for backwards compatibility with tests instantiating directly
    public MinioStorageService(MinioClient minioClient, String defaultBucketName) {
        this(minioClient, defaultBucketName, "submissions");
    }

    private void ensureBucketExists(String bucket) {
        if (bucket == null || bucket.isBlank() || initializedBuckets.contains(bucket)) {
            return;
        }
        synchronized (this) {
            if (initializedBuckets.contains(bucket)) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build()
                );
                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder().bucket(bucket).build()
                    );
                }
                initializedBuckets.add(bucket);
            } catch (Exception e) {
                throw new StorageException("Failed to initialize MinIO bucket: " + bucket, e);
            }
        }
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, long size, String contentType) {
        String effectiveBucket = (bucketName != null && !bucketName.isBlank()) ? bucketName : defaultBucketName;
        ensureBucketExists(effectiveBucket);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(effectiveBucket)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            return "minio://" + effectiveBucket + "/" + objectName;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to MinIO: " + objectName + " in bucket: " + effectiveBucket, e);
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectName) {
        String effectiveBucket = (bucketName != null && !bucketName.isBlank()) ? bucketName : defaultBucketName;
        ensureBucketExists(effectiveBucket);
        String resolvedName = extractObjectName(objectName);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(effectiveBucket)
                            .object(resolvedName)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to download file from MinIO: " + objectName + " in bucket: " + effectiveBucket, e);
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) {
        String effectiveBucket = (bucketName != null && !bucketName.isBlank()) ? bucketName : defaultBucketName;
        ensureBucketExists(effectiveBucket);
        String resolvedName = extractObjectName(objectName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(effectiveBucket)
                            .object(resolvedName)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from MinIO: " + objectName + " in bucket: " + effectiveBucket, e);
        }
    }

    @Override
    public boolean fileExists(String bucketName, String objectName) {
        String effectiveBucket = (bucketName != null && !bucketName.isBlank()) ? bucketName : defaultBucketName;
        ensureBucketExists(effectiveBucket);
        String resolvedName = extractObjectName(objectName);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(effectiveBucket)
                            .object(resolvedName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException("Error checking file existence in MinIO: " + objectName + " in bucket: " + effectiveBucket, e);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getBucketName() {
        return defaultBucketName;
    }

    @Override
    public String getSubmissionsBucketName() {
        return submissionsBucketName;
    }
}
