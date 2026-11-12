package jsd.toiyeujava.minileetcode.plugin.workspace;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safe ZIP archive extractor adhering strictly to the Ponytail principle (zero external dependencies).
 * Features:
 * <ul>
 *     <li>Strict Zip Slip prevention verifying all target paths stay within targetDir.</li>
 *     <li>Non-overwrite policy (FR-PL-002, AC-004) preserving existing user files.</li>
 *     <li>Automatic parent directory creation before file writes.</li>
 *     <li>Optional source routing for .java files into {@code src/main/java/} for Maven project layout.</li>
 * </ul>
 */
public class ArchiveExtractor {

    /** Chống zip bomb; cùng mức với giới hạn bài nộp phía backend (100 entry, 20 MB). */
    public static final int MAX_ENTRIES = 200;
    public static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;

    /**
     * Immutable outcome of an extraction operation.
     *
     * @param extractedFiles count of newly created files
     * @param skippedFiles   count of files skipped because they already existed
     * @param writtenPaths   unmodifiable list of newly created file paths
     */
    public record ExtractionResult(
            int extractedFiles,
            int skippedFiles,
            List<Path> writtenPaths
    ) {
        public ExtractionResult {
            writtenPaths = (writtenPaths == null) ? List.of() : List.copyOf(writtenPaths);
        }

        public int totalFiles() {
            return extractedFiles + skippedFiles;
        }
    }

    public ArchiveExtractor() {
    }

    /**
     * Extracts a ZIP archive from in-memory byte array without routing.
     *
     * @param zipBytes  raw ZIP archive bytes
     * @param targetDir target directory path
     * @return extraction result summary
     * @throws IOException       if an I/O error or archive corruption occurs
     * @throws SecurityException if a Zip Slip entry is detected
     */
    public ExtractionResult extract(byte[] zipBytes, Path targetDir) throws IOException {
        return extract(zipBytes, targetDir, false);
    }

    /**
     * Extracts a ZIP archive from an input stream without routing.
     *
     * @param zipStream stream supplying ZIP archive bytes
     * @param targetDir target directory path
     * @return extraction result summary
     * @throws IOException       if an I/O error or archive corruption occurs
     * @throws SecurityException if a Zip Slip entry is detected
     */
    public ExtractionResult extract(InputStream zipStream, Path targetDir) throws IOException {
        return extract(zipStream, targetDir, false);
    }

    /**
     * Extracts a ZIP archive from in-memory byte array with optional .java source routing.
     *
     * @param zipBytes         raw ZIP archive bytes
     * @param targetDir        target directory path
     * @param routeJavaSources if true, .java files not already under src/ will be routed into src/main/java/
     * @return extraction result summary
     * @throws IOException       if an I/O error or archive corruption occurs
     * @throws SecurityException if a Zip Slip entry is detected
     */
    public ExtractionResult extract(byte[] zipBytes, Path targetDir, boolean routeJavaSources) throws IOException {
        Objects.requireNonNull(zipBytes, "zipBytes must not be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes)) {
            return extract(bais, targetDir, routeJavaSources);
        }
    }

    /**
     * Extracts a ZIP archive from an input stream with optional .java source routing.
     *
     * @param zipStream        stream supplying ZIP archive bytes
     * @param targetDir        target directory path
     * @param routeJavaSources if true, .java files not already under src/ will be routed into src/main/java/
     * @return extraction result summary
     * @throws IOException       if an I/O error or archive corruption occurs
     * @throws SecurityException if a Zip Slip entry is detected
     */
    public ExtractionResult extract(InputStream zipStream, Path targetDir, boolean routeJavaSources) throws IOException {
        Objects.requireNonNull(zipStream, "zipStream must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
        if (Files.notExists(normalizedTargetDir)) {
            Files.createDirectories(normalizedTargetDir);
        }

        int extractedFiles = 0;
        int skippedFiles = 0;
        int entryCount = 0;
        long totalBytes = 0;
        List<Path> writtenPaths = new ArrayList<>();
        InputStream markable = zipStream.markSupported() ? zipStream : new java.io.BufferedInputStream(zipStream);
        markable.mark(4);
        byte[] header = markable.readNBytes(4);
        markable.reset();

        if (header.length > 0) {
            if (header.length < 4 || header[0] != 0x50 || header[1] != 0x4B) {
                throw new java.util.zip.ZipException("Invalid ZIP archive: missing or corrupted ZIP magic header");
            }
        }

        try (ZipInputStream zis = new ZipInputStream(markable)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) {
                    zis.closeEntry();
                    continue;
                }

                // Cross-platform Zip Slip protection: normalize path separators
                String normalizedName = rawName.replace('\\', '/');

                // Optional routing for Maven projects: place bare Java sources into src/main/java/
                if (routeJavaSources && !entry.isDirectory() && normalizedName.endsWith(".java")) {
                    if (!normalizedName.startsWith("src/") && !normalizedName.startsWith("/")) {
                        normalizedName = "src/main/java/" + normalizedName;
                    }
                }

                if (++entryCount > MAX_ENTRIES) {
                    throw new java.util.zip.ZipException("Archive has more than " + MAX_ENTRIES + " entries");
                }

                Path target;
                try {
                    target = normalizedTargetDir.resolve(normalizedName).normalize();
                } catch (java.nio.file.InvalidPathException e) {
                    // vd. "aux.java" hoặc tên có ':' trên Windows
                    throw new IOException("Archive entry has an invalid file name on this OS: " + rawName, e);
                }

                // Strict Zip Slip boundary verification
                if (!target.startsWith(normalizedTargetDir)) {
                    throw new SecurityException("Zip Slip detected: " + rawName);
                }

                // Ignore entries that resolve directly to the extraction root
                if (target.equals(normalizedTargetDir)) {
                    zis.closeEntry();
                    continue;
                }

                // Directory entry handling
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    zis.closeEntry();
                    continue;
                }

                // Non-overwrite policy (FR-PL-002, AC-004): preserve student's existing work
                if (Files.exists(target)) {
                    skippedFiles++;
                    zis.closeEntry();
                    continue;
                }

                // Ensure all intermediate parent directories exist
                Path parent = target.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                // Write file contents safely using CREATE_NEW
                try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw new java.util.zip.ZipException("Archive expands beyond " + MAX_TOTAL_BYTES + " bytes");
                        }
                        out.write(buffer, 0, read);
                    }
                }

                extractedFiles++;
                writtenPaths.add(target);
                zis.closeEntry();
            }
        }

        return new ExtractionResult(extractedFiles, skippedFiles, writtenPaths);
    }

    /**
     * Convenience method extracting archive while automatically routing bare .java files to src/main/java/.
     */
    public ExtractionResult extractRoutingJava(byte[] zipBytes, Path targetDir) throws IOException {
        return extract(zipBytes, targetDir, true);
    }

    /**
     * Convenience method extracting archive stream while automatically routing bare .java files to src/main/java/.
     */
    public ExtractionResult extractRoutingJava(InputStream zipStream, Path targetDir) throws IOException {
        return extract(zipStream, targetDir, true);
    }
}
