package jsd.toiyeujava.mini_leetcode;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.storage.service.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private StorageService storageService;
    private final String bucketName = "testcases";

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(minioClient, bucketName);
    }

    @Test
    @DisplayName("Verification Gate 3: uploadFile should stream file directly and create bucket if not existing")
    void uploadFile_createsBucketAndStreamsDirectly() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        byte[] content = "1 2\n3 4\n".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);

        String uri = storageService.uploadFile("problems/1/input_1.txt", inputStream, content.length, "text/plain");

        assertThat(uri).isEqualTo("minio://testcases/problems/1/input_1.txt");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs putArgs = captor.getValue();
        assertThat(putArgs.bucket()).isEqualTo(bucketName);
        assertThat(putArgs.object()).isEqualTo("problems/1/input_1.txt");
        assertThat(putArgs.contentType()).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("Verification Gate 3: downloadFile should stream file from MinIO")
    void downloadFile_returnsInputStream() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        InputStream result = storageService.downloadFile("problems/1/input_1.txt");

        assertThat(result).isSameAs(mockResponse);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("Verification Gate 3: deleteFile should remove object from MinIO")
    void deleteFile_removesObject() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        storageService.deleteFile("problems/1/input_1.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("Verification Gate 3: MinIO errors should be wrapped in StorageException")
    void uploadFile_wrapsExceptionInStorageException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("MinIO network failure"));

        byte[] content = "test".getBytes();
        InputStream is = new ByteArrayInputStream(content);

        assertThatThrownBy(() -> storageService.uploadFile("error.txt", is, content.length, "text/plain"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to upload file to MinIO");
    }

    @Test
    @DisplayName("Verification Gate 3: getBucketName returns configured bucket")
    void getBucketName_returnsBucket() {
        assertThat(storageService.getBucketName()).isEqualTo(bucketName);
    }

    @Test
    @DisplayName("Verification Gate 3: fileExists returns true when statObject succeeds")
    void fileExists_whenExists_returnsTrue() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));

        assertThat(storageService.fileExists("problems/1/input.txt")).isTrue();
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    @DisplayName("Verification Gate 3: fileExists returns false when NoSuchKey error occurs")
    void fileExists_whenNoSuchKey_returnsFalse() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("NoSuchKey");
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(errorResponse);

        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(exception);

        assertThat(storageService.fileExists("problems/1/input.txt")).isFalse();
    }

    @Test
    @DisplayName("Verification Gate 3: fileExists throws StorageException when other MinIO error occurs")
    void fileExists_whenOtherMinioError_throwsStorageException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.code()).thenReturn("AccessDenied");
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(errorResponse);

        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(exception);

        assertThatThrownBy(() -> storageService.fileExists("problems/1/input.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Error checking file existence in MinIO");
    }

    @Test
    @DisplayName("Verification Gate 3: fileExists returns false when generic exception occurs")
    void fileExists_whenGenericException_returnsFalse() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("Connection timeout"));

        assertThat(storageService.fileExists("problems/1/input.txt")).isFalse();
    }

    @Test
    @DisplayName("Verification Gate 3: downloadFile wraps exception in StorageException")
    void downloadFile_wrapsException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("MinIO get failure"));

        assertThatThrownBy(() -> storageService.downloadFile("missing.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to download file from MinIO");
    }

    @Test
    @DisplayName("Verification Gate 3: deleteFile wraps exception in StorageException")
    void deleteFile_wrapsException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doThrow(new RuntimeException("MinIO delete failure")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> storageService.deleteFile("file.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to delete file from MinIO");
    }

    @Test
    @DisplayName("Verification Gate 3: bucket initialization failure throws StorageException")
    void ensureBucketExists_fails_throwsStorageException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> storageService.downloadFile("file.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to initialize MinIO bucket");
    }

    @Test
    @DisplayName("Verification Gate 3: deleteFileByUri parses URI and deletes object via removeObject")
    void deleteFileByUri_success() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        storageService.deleteFileByUri("minio://testcases/problems/1/testcases/in.txt");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("problems/1/testcases/in.txt");
        assertThat(captor.getValue().bucket()).isEqualTo(bucketName);

        // Edge case: null or blank URI should be safely ignored
        storageService.deleteFileByUri(null);
        storageService.deleteFileByUri("   ");
    }

    @Test
    @DisplayName("Verification Gate 3: downloadFile handles full minio:// URI transparently")
    void downloadFile_withFullUri_stripsSchemeAndBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        InputStream result = storageService.downloadFile("minio://testcases/submissions/10/1/uuid.java");

        assertThat(result).isSameAs(mockResponse);
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("submissions/10/1/uuid.java");
    }

    @Test
    @DisplayName("Verification Gate 3: extractObjectName and downloadFileByUri handle null or blank")
    void extractObjectName_and_downloadFileByUri_edgeCases() {
        assertThat(storageService.extractObjectName(null)).isEmpty();
        assertThat(storageService.extractObjectName("   ")).isEmpty();
        assertThat(storageService.extractObjectName("raw/path/file.txt")).isEqualTo("raw/path/file.txt");
        assertThat(storageService.downloadFileByUri(null)).isNull();
        assertThat(storageService.downloadFileByUri("")).isNull();
    }
}
