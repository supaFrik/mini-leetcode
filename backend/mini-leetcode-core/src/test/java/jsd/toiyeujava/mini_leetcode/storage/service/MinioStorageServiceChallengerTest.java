package jsd.toiyeujava.mini_leetcode.storage.service;

import io.minio.*;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceChallengerTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService storageService;

    private static final String DEFAULT_BUCKET = "testcases";
    private static final String SUBMISSIONS_BUCKET = "submissions";

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(minioClient, DEFAULT_BUCKET, SUBMISSIONS_BUCKET);
    }

    // =========================================================================
    // 1. URI Parsing & Edge Cases (extractBucketName, extractObjectName, downloadFileByUri)
    // =========================================================================
    @Nested
    @DisplayName("Challenge Dimension 1: URI Parsing & Adversarial Formats")
    class UriParsingEdgeCases {

        @Test
        @DisplayName("URI with trailing slash: minio://submissions/ has bucket 'submissions' and empty object")
        void uri_WithTrailingSlash() {
            String uri = "minio://submissions/";
            assertThat(storageService.extractBucketName(uri)).isEqualTo("submissions");
            assertThat(storageService.extractObjectName(uri)).isEmpty();
        }

        @Test
        @DisplayName("URI without slash after bucket: minio://testcases returns null bucket and 'testcases' object")
        void uri_WithoutSlashAfterBucket() {
            String uri = "minio://testcases";
            // No slash after bucket, so extractBucketName cannot demarcate bucket from object
            assertThat(storageService.extractBucketName(uri)).isNull();
            assertThat(storageService.extractObjectName(uri)).isEqualTo("testcases");
        }

        @Test
        @DisplayName("URI with empty bucket: minio:///empty returns empty bucket and 'empty' object")
        void uri_EmptyBucket() {
            String uri = "minio:///empty";
            assertThat(storageService.extractBucketName(uri)).isEmpty();
            assertThat(storageService.extractObjectName(uri)).isEqualTo("empty");
        }

        @Test
        @DisplayName("URI with http scheme: http://minio/submissions/code.zip parses scheme agnostic")
        void uri_HttpScheme() {
            String uri = "http://minio/submissions/code.zip";
            assertThat(storageService.extractBucketName(uri)).isEqualTo("minio");
            assertThat(storageService.extractObjectName(uri)).isEqualTo("submissions/code.zip");
        }

        @Test
        @DisplayName("URI with http scheme and no path: http://minio/ has bucket 'minio' and empty object")
        void uri_HttpSchemeTrailingSlashOnly() {
            String uri = "http://minio/";
            assertThat(storageService.extractBucketName(uri)).isEqualTo("minio");
            assertThat(storageService.extractObjectName(uri)).isEmpty();
        }

        @ParameterizedTest(name = "Malformed URI: \"{0}\"")
        @ValueSource(strings = {"minio://", "://", "minio:/test", "just-a-plain-key.zip"})
        void uri_MalformedSchemes(String malformedUri) {
            String bucket = storageService.extractBucketName(malformedUri);
            // Must safely return null without throwing StringIndexOutOfBoundsException
            assertThat(bucket).isNull();
        }

        @ParameterizedTest(name = "Null or Blank URI: \"{0}\"")
        @ValueSource(strings = {"", "   ", "\t\n"})
        void uri_NullAndBlank_ReturnsNullOrEmpty(String blankUri) {
            assertThat(storageService.extractBucketName(blankUri)).isNull();
            assertThat(storageService.extractObjectName(blankUri)).isEmpty();
            assertThat(storageService.downloadFileByUri(blankUri)).isNull();
        }

        @Test
        @DisplayName("downloadFileByUri routes minio:///empty to default bucket")
        void downloadFileByUri_EmptyBucket_FallsBackToDefault() throws Exception {
            when(minioClient.bucketExists(any())).thenReturn(true);
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mock(GetObjectResponse.class));

            storageService.downloadFileByUri("minio:///empty");

            ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
            verify(minioClient).getObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(DEFAULT_BUCKET);
            assertThat(captor.getValue().object()).isEqualTo("empty");
        }

        @Test
        @DisplayName("deleteFileByUri safely ignores empty object names")
        void deleteFileByUri_EmptyObjectName_NoOp() throws Exception {
            storageService.deleteFileByUri("minio://submissions/");
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        }
    }

    // =========================================================================
    // 2. Dynamic Bucket Creation & Concurrency Thread Safety
    // =========================================================================
    @Nested
    @DisplayName("Challenge Dimension 2: Concurrency & Dynamic Bucket Initialization")
    class ConcurrencyAndThreadSafety {

        @Test
        @DisplayName("100 concurrent threads creating the same dynamic bucket: bucketExists/makeBucket called exactly once")
        void concurrentDynamicBucketCreation_ThreadSafe() throws Exception {
            String dynamicBucket = "dynamic-contest-bucket";
            AtomicInteger bucketExistsCount = new AtomicInteger(0);
            AtomicInteger makeBucketCount = new AtomicInteger(0);

            when(minioClient.bucketExists(any())).thenAnswer(invocation -> {
                bucketExistsCount.incrementAndGet();
                Thread.sleep(10); // Simulate network latency
                return false;
            });
            doAnswer(invocation -> {
                makeBucketCount.incrementAndGet();
                Thread.sleep(10); // Simulate network latency
                return null;
            }).when(minioClient).makeBucket(any(MakeBucketArgs.class));
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        byte[] data = ("payload-" + idx).getBytes();
                        storageService.uploadFile(dynamicBucket, "file-" + idx + ".txt",
                                new ByteArrayInputStream(data), data.length, "text/plain");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Release all threads at once
            boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(finished).isTrue();
            // Critical assertion: makeBucket and bucketExists should be executed exactly ONCE due to double-checked locking
            assertThat(makeBucketCount.get()).isEqualTo(1);
            assertThat(bucketExistsCount.get()).isEqualTo(1);
            verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));
            verify(minioClient, times(threadCount)).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("Concurrent uploads to multiple distinct buckets initialize each bucket once")
        void concurrentMultiBucketDynamicCreation() throws Exception {
            when(minioClient.bucketExists(any())).thenReturn(false);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

            String[] buckets = {"bucket-alpha", "bucket-beta", "bucket-gamma", "bucket-delta"};
            int threadsPerBucket = 20;
            int totalThreads = buckets.length * threadsPerBucket;

            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalThreads);

            for (String b : buckets) {
                for (int i = 0; i < threadsPerBucket; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            byte[] data = "test".getBytes();
                            storageService.uploadFile(b, "key-" + idx, new ByteArrayInputStream(data), data.length, "text/plain");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(finished).isTrue();
            // Exactly 4 makeBucket calls (one for each unique bucket)
            verify(minioClient, times(4)).makeBucket(any(MakeBucketArgs.class));
            verify(minioClient, times(totalThreads)).putObject(any(PutObjectArgs.class));
        }
    }

    // =========================================================================
    // 3. Memory Safety & Streaming Uploads
    // =========================================================================
    @Nested
    @DisplayName("Challenge Dimension 3: Memory Safety & Zero-Copy Streaming")
    class MemorySafetyAndStreaming {

        @Test
        @DisplayName("uploadFile passes the original InputStream directly to MinIO without reading into memory")
        void uploadFile_StreamsWithoutBuffering() throws Exception {
            when(minioClient.bucketExists(any())).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

            // Custom non-buffered stream that counts read bytes
            AtomicInteger bytesRead = new AtomicInteger(0);
            InputStream trackingStream = new InputStream() {
                private int count = 0;
                private final int max = 1000;

                @Override
                public int read() {
                    if (count >= max) return -1;
                    count++;
                    bytesRead.incrementAndGet();
                    return 0x42;
                }
            };

            storageService.uploadFile(SUBMISSIONS_BUCKET, "stream-test.bin", trackingStream, 1000L, "application/octet-stream");

            ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
            verify(minioClient).putObject(captor.capture());

            PutObjectArgs args = captor.getValue();
            assertThat(args.bucket()).isEqualTo(SUBMISSIONS_BUCKET);
            assertThat(args.object()).isEqualTo("stream-test.bin");
            assertThat(args.objectSize()).isEqualTo(1000L);
            // MinIO SDK resolves partSize -1 to objectSize for objects <= 5GB
            assertThat(args.partSize()).isEqualTo(1000L);
            // MinIO wraps unbuffered streams in a lightweight BufferedInputStream (8KB buffer), zero memory spike
            assertThat(args.stream()).isInstanceOf(java.io.BufferedInputStream.class);
            // Because minioClient is mocked, the stream has NOT been read into memory by MinioStorageService
            assertThat(bytesRead.get()).isZero();
        }

        @Test
        @DisplayName("uploadFile handles 20MB stream with exact size specified")
        void uploadFile_20MBStreamSizeCheck() throws Exception {
            when(minioClient.bucketExists(any())).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

            long twentyMB = 20 * 1024 * 1024L;
            InputStream dummyStream = new InputStream() {
                @Override
                public int read() {
                    return 0;
                }
            };

            storageService.uploadFile(SUBMISSIONS_BUCKET, "large.zip", dummyStream, twentyMB, "application/zip");

            ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
            verify(minioClient).putObject(captor.capture());

            PutObjectArgs args = captor.getValue();
            assertThat(args.objectSize()).isEqualTo(twentyMB);
            assertThat(args.stream()).isInstanceOf(java.io.BufferedInputStream.class);
        }
    }

    // =========================================================================
    // 4. Submission Key Uniqueness & Format Verification
    // =========================================================================
    @Nested
    @DisplayName("Challenge Dimension 4: Key Uniqueness & Format")
    class KeyUniquenessAndFormat {

        private static final Pattern KEY_REGEX = Pattern.compile("^submissions/\\d+/\\d+/[0-9a-fA-F\\-]{36}\\.zip$");

        @Test
        @DisplayName("generateSubmissionZipKey matches standard submissions/{problemId}/{userId}/{uuid}.zip format")
        void keyFormat_MatchesStandard() {
            String key = storageService.generateSubmissionZipKey(999L, 12345L);
            assertThat(key).matches(KEY_REGEX);
            assertThat(key).startsWith("submissions/999/12345/");
            assertThat(key).endsWith(".zip");
        }

        @Test
        @DisplayName("generateSubmissionZipKey produces 10,000 unique keys without collision")
        void keyUniqueness_NoCollisionsAcross10000Keys() throws InterruptedException {
            int count = 10000;
            Set<String> generatedKeys = Collections.synchronizedSet(new HashSet<>(count));
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(count);

            for (int i = 0; i < count; i++) {
                final long pId = i % 100;
                final long uId = i % 50;
                executor.submit(() -> {
                    try {
                        generatedKeys.add(storageService.generateSubmissionZipKey(pId, uId));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(finished).isTrue();
            assertThat(generatedKeys).hasSize(count);
        }

        @ParameterizedTest(name = "Extension formatting: \"{0}\" -> endsWith \"{1}\"")
        @CsvSource({
                "zip, .zip",
                ".zip, .zip",
                "tar.gz, .tar.gz",
                ".tar.gz, .tar.gz",
                "'', .zip"
        })
        void generateSubmissionKey_ExtensionFormatting(String inputExt, String expectedSuffix) {
            String key = storageService.generateSubmissionKey(1L, 2L, inputExt);
            assertThat(key).endsWith(expectedSuffix);
            assertThat(key).doesNotContain("..zip");
        }

        @Test
        @DisplayName("Null extension defaults cleanly to .zip")
        void generateSubmissionKey_NullExtensionDefaultsToZip() {
            String key = storageService.generateSubmissionKey(1L, 2L, null);
            assertThat(key).endsWith(".zip");
        }
    }
}
