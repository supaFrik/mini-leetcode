package jsd.toiyeujava.mini_leetcode.storage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private volatile boolean bucketInitialized = false;

    public MinioStorageService(
            MinioClient minioClient,
            @Value("${app.minio.bucket-name:testcases}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    private synchronized void ensureBucketExists() {
        if (bucketInitialized) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
            }
            bucketInitialized = true;
        } catch (Exception e) {
            throw new StorageException("Failed to initialize MinIO bucket: " + bucketName, e);
        }
    }

    @Override
    public String uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        ensureBucketExists();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            return "minio://" + bucketName + "/" + objectName;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to MinIO: " + objectName, e);
        }
    }

    @Override
    public InputStream downloadFile(String objectName) {
        ensureBucketExists();
        String resolvedName = extractObjectName(objectName);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(resolvedName)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to download file from MinIO: " + objectName, e);
        }
    }

    @Override
    public void deleteFile(String objectName) {
        ensureBucketExists();
        String resolvedName = extractObjectName(objectName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(resolvedName)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from MinIO: " + objectName, e);
        }
    }

    @Override
    public boolean fileExists(String objectName) {
        ensureBucketExists();
        String resolvedName = extractObjectName(objectName);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(resolvedName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException("Error checking file existence in MinIO: " + objectName, e);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getBucketName() {
        return bucketName;
    }
}
