package jsd.toiyeujava.mini_leetcode.storage.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceSubmissionsTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(minioClient, "testcases", "submissions");
    }

    @Test
    @DisplayName("STO-01 & STO-02: uploadSubmission should target submissions bucket with standard key")
    void uploadSubmission_RoutesToSubmissionsBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        byte[] zipData = new byte[]{0x50, 0x4B, 0x03, 0x04};
        InputStream is = new ByteArrayInputStream(zipData);

        String uri = storageService.uploadSubmission(12L, 34L, is, zipData.length);

        assertThat(uri).startsWith("minio://submissions/submissions/12/34/").endsWith(".zip");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo("submissions");
        assertThat(args.contentType()).isEqualTo("application/zip");
        assertThat(args.object()).matches("submissions/12/34/[a-f0-9\\-]+\\.zip");
    }

    @Test
    @DisplayName("STO-03: downloadFileByUri should route minio://submissions/ to submissions bucket")
    void downloadFileByUri_RoutesToSubmissionsBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        InputStream result = storageService.downloadFileByUri("minio://submissions/submissions/10/1/abc.zip");

        assertThat(result).isSameAs(mockResponse);
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("submissions");
        assertThat(captor.getValue().object()).isEqualTo("submissions/10/1/abc.zip");
    }

    @Test
    @DisplayName("STO-04: Existing testcase uploads must continue targeting testcases bucket")
    void backwardCompatibility_UploadTestcase_UsesTestcasesBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        byte[] data = "1 2\n3".getBytes();
        String uri = storageService.uploadFile("problems/1/input.txt", new ByteArrayInputStream(data), data.length, "text/plain");

        assertThat(uri).isEqualTo("minio://testcases/problems/1/input.txt");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("testcases");
    }

    @Test
    @DisplayName("STO-05: extractBucketName correctly extracts bucket from various URIs")
    void extractBucketName_ExtractsCorrectly() {
        assertThat(storageService.extractBucketName("minio://submissions/submissions/10/1/uuid.zip")).isEqualTo("submissions");
        assertThat(storageService.extractBucketName("minio://testcases/problems/1/input.txt")).isEqualTo("testcases");
        assertThat(storageService.extractBucketName("raw/path/file.txt")).isNull();
        assertThat(storageService.extractBucketName("")).isNull();
        assertThat(storageService.extractBucketName(null)).isNull();
    }

    @Test
    @DisplayName("STO-06: fileExists checks correct bucket and handles existence")
    void fileExists_ChecksCorrectBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));

        boolean exists = storageService.fileExists("submissions", "submissions/1/2/test.zip");

        assertThat(exists).isTrue();
        ArgumentCaptor<StatObjectArgs> captor = ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(minioClient).statObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("submissions");
        assertThat(captor.getValue().object()).isEqualTo("submissions/1/2/test.zip");
    }

    @Test
    @DisplayName("STO-07: deleteFileByUri routes deletion to appropriate bucket")
    void deleteFileByUri_RoutesToCorrectBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);

        storageService.deleteFileByUri("minio://submissions/submissions/1/2/old.zip");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("submissions");
        assertThat(captor.getValue().object()).isEqualTo("submissions/1/2/old.zip");
    }
}
