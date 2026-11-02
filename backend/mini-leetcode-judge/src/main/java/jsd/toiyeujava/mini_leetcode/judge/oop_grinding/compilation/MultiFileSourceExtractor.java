package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class MultiFileSourceExtractor {

    public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 20 * 1024 * 1024L; // 20 MB
    public static final int MAX_ENTRIES = 100;
    public static final long MAX_SINGLE_FILE_SIZE = 20 * 1024 * 1024L;
    private static final int BUFFER_SIZE = 8192;

    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] EMPTY_ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x05, 0x06};
    private static final String BASE64_ZIP_PREFIX = "UEsDB";

    private static final Pattern DELIMITED_HEADER_PATTERN =
            Pattern.compile("(?m)^//\\s*(?:===|---|###)?\\s*FILE:\\s*([^\\r\\n]+?)\\s*(?:===|---|###)?\\s*$");

    private static final Pattern PUBLIC_TYPE_PATTERN =
            Pattern.compile("public\\s+(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");

    private static final List<String> FORBIDDEN_EXTENSIONS = List.of(
            ".exe", ".dll", ".so", ".sh", ".bat", ".cmd", ".jar", ".class"
    );

    private final ObjectMapper objectMapper;

    public MultiFileSourceExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public MultiFileSourceExtractor() {
        this(new ObjectMapper());
    }

    /**
     * Extracts source files from an InputStream (ZIP byte stream).
     */
    public List<Path> extract(InputStream inputStream, Path targetDir) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        return extractZipStream(inputStream, targetDir);
    }

    /**
     * Extracts source files from raw byte array (ZIP bytes).
     */
    public List<Path> extract(byte[] zipBytes, Path targetDir) throws IOException {
        if (zipBytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        return extractZipStream(new ByteArrayInputStream(zipBytes), targetDir);
    }

    /**
     * Extracts source files from a ZIP file on disk.
     */
    public List<Path> extract(Path zipFile, Path targetDir) throws IOException {
        if (zipFile == null || !Files.exists(zipFile)) {
            throw new IllegalArgumentException("Zip file path does not exist: " + zipFile);
        }
        try (InputStream is = Files.newInputStream(zipFile)) {
            return extractZipStream(is, targetDir);
        }
    }

    /**
     * Smart extractor for String inputs:
     * 1. Base64-encoded ZIP
     * 2. Delimited text bundle (// === FILE: ... ===)
     * 3. JSON bundle ({"files": [...]} or {"path": "content"})
     * 4. Single-file Java source code fallback
     */
    public List<Path> extract(String sourceBundleOrText, Path targetDir) throws IOException {
        if (sourceBundleOrText == null || sourceBundleOrText.isBlank()) {
            throw new IllegalArgumentException("Source code or bundle cannot be empty");
        }

        String trimmed = sourceBundleOrText.trim();

        // 1. Base64 ZIP detection
        if (isBase64Zip(trimmed)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                return extract(decoded, targetDir);
            } catch (IllegalArgumentException e) {
                log.debug("Base64 decode failed, falling back to text parsing: {}", e.getMessage());
            }
        }

        // 2. Delimited bundle detection (e.g. // === FILE: ... ===)
        if (DELIMITED_HEADER_PATTERN.matcher(trimmed).find()) {
            return extractDelimitedBundle(trimmed, targetDir);
        }

        // 3. JSON bundle detection (starts with {)
        if (trimmed.startsWith("{") && (trimmed.contains("\"files\"") || trimmed.contains(".java"))) {
            try {
                return extractJsonBundle(trimmed, targetDir);
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                log.debug("JSON parsing failed, falling back to single file: {}", e.getMessage());
            }
        }

        // 4. Single-file Java source fallback
        return extractSingleJavaFile(trimmed, targetDir);
    }

    // =========================================================================
    // ZIP STREAM EXTRACTION WITH AIRTIGHT ZIP SLIP & ZIP BOMB PREVENTION
    // =========================================================================

    private List<Path> extractZipStream(InputStream inputStream, Path targetDir) throws IOException {
        Path targetDirNorm = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetDirNorm);

        List<Path> extractedFiles = new ArrayList<>();
        long totalBytesExtracted = 0;
        int entryCount = 0;
        Path currentWritingFile = null;

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();

                // Skip macOS metadata artifacts and .DS_Store
                if (isIgnoredMetadata(rawName)) {
                    zis.closeEntry();
                    continue;
                }

                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new SecurityException("Archive exceeds maximum entry count of " + MAX_ENTRIES);
                }

                Path resolvedPath = validateAndResolvePath(rawName, targetDirNorm);

                if (entry.isDirectory() || rawName.endsWith("/") || rawName.endsWith("\\")) {
                    Files.createDirectories(resolvedPath);
                } else {
                    if (resolvedPath.getParent() != null) {
                        Files.createDirectories(resolvedPath.getParent());
                    }

                    long fileBytes = 0;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    currentWritingFile = resolvedPath;
                    try (OutputStream fos = Files.newOutputStream(resolvedPath,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fileBytes += len;
                            totalBytesExtracted += len;

                            if (fileBytes > MAX_SINGLE_FILE_SIZE) {
                                throw new SecurityException("File [" + rawName + "] exceeds max size limit of 20MB");
                            }
                            if (totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                                throw new SecurityException("Archive uncompressed size exceeds maximum allowed limit of 20MB");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }
                    extractedFiles.add(resolvedPath);
                    currentWritingFile = null;
                }
                zis.closeEntry();
            }
        } catch (Throwable t) {
            if (currentWritingFile != null) {
                try {
                    Files.deleteIfExists(currentWritingFile);
                } catch (Exception ignored) {
                }
            }
            cleanup(extractedFiles);
            if (t instanceof IOException e) {
                throw e;
            }
            if (t instanceof RuntimeException e) {
                throw e;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new RuntimeException(t);
        }

        if (extractedFiles.isEmpty()) {
            throw new IllegalArgumentException("Archive contains no regular files to extract");
        }
        return extractedFiles;
    }

    // =========================================================================
    // DELIMITED TEXT BUNDLE EXTRACTION
    // =========================================================================

    private List<Path> extractDelimitedBundle(String bundleText, Path targetDir) throws IOException {
        Path targetDirNorm = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetDirNorm);

        List<Path> extractedFiles = new ArrayList<>();
        Matcher matcher = DELIMITED_HEADER_PATTERN.matcher(bundleText);

        List<Integer> startIndices = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();

        while (matcher.find()) {
            startIndices.add(matcher.end());
            filePaths.add(matcher.group(1).trim());
        }

        long totalBytesExtracted = 0;
        int entryCount = filePaths.size();
        if (entryCount > MAX_ENTRIES) {
            throw new SecurityException("Bundle exceeds maximum entry count of " + MAX_ENTRIES);
        }

        Path currentWritingFile = null;
        try {
            for (int i = 0; i < filePaths.size(); i++) {
                String filePath = filePaths.get(i);
                int contentStart = startIndices.get(i);
                int contentEnd = (i + 1 < filePaths.size())
                        ? findHeaderStart(bundleText, matcher, i + 1)
                        : bundleText.length();

                String content = bundleText.substring(contentStart, contentEnd);
                if (content.startsWith("\r\n")) {
                    content = content.substring(2);
                } else if (content.startsWith("\n")) {
                    content = content.substring(1);
                }

                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                totalBytesExtracted += contentBytes.length;
                if (totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    throw new SecurityException("Bundle uncompressed size exceeds maximum allowed limit of 20MB");
                }

                Path resolved = validateAndResolvePath(filePath, targetDirNorm);

                if (resolved.getParent() != null) {
                    Files.createDirectories(resolved.getParent());
                }
                currentWritingFile = resolved;
                Files.write(resolved, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                extractedFiles.add(resolved);
                currentWritingFile = null;
            }
        } catch (Throwable t) {
            if (currentWritingFile != null) {
                try {
                    Files.deleteIfExists(currentWritingFile);
                } catch (Exception ignored) {
                }
            }
            cleanup(extractedFiles);
            if (t instanceof IOException e) {
                throw e;
            }
            if (t instanceof RuntimeException e) {
                throw e;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
        return extractedFiles;
    }

    private int findHeaderStart(String bundleText, Matcher matcher, int index) {
        matcher.reset();
        int count = 0;
        while (matcher.find()) {
            if (count == index) {
                return matcher.start();
            }
            count++;
        }
        return bundleText.length();
    }

    // =========================================================================
    // JSON BUNDLE EXTRACTION
    // =========================================================================

    private List<Path> extractJsonBundle(String jsonText, Path targetDir) throws IOException {
        Path targetDirNorm = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetDirNorm);

        List<Path> extractedFiles = new ArrayList<>();
        JsonNode root = objectMapper.readTree(jsonText);
        long totalBytesExtracted = 0;
        int entryCount = 0;
        Path[] currentWritingRef = new Path[1];

        try {
            if (root.has("files") && root.get("files").isArray()) {
                for (JsonNode fileNode : root.get("files")) {
                    entryCount++;
                    if (entryCount > MAX_ENTRIES) {
                        throw new SecurityException("JSON bundle exceeds maximum entry count of " + MAX_ENTRIES);
                    }
                    String path = fileNode.path("path").asText();
                    String content = fileNode.path("content").asText("");
                    totalBytesExtracted = writeContentToFile(path, content, targetDirNorm, extractedFiles, totalBytesExtracted, currentWritingRef);
                }
            } else if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    entryCount++;
                    if (entryCount > MAX_ENTRIES) {
                        throw new SecurityException("JSON bundle exceeds maximum entry count of " + MAX_ENTRIES);
                    }
                    String path = field.getKey();
                    String content = field.getValue().isTextual() ? field.getValue().asText() : field.getValue().toString();
                    totalBytesExtracted = writeContentToFile(path, content, targetDirNorm, extractedFiles, totalBytesExtracted, currentWritingRef);
                }
            }
        } catch (Throwable t) {
            if (currentWritingRef[0] != null) {
                try {
                    Files.deleteIfExists(currentWritingRef[0]);
                } catch (Exception ignored) {
                }
            }
            cleanup(extractedFiles);
            if (t instanceof IOException e) {
                throw e;
            }
            if (t instanceof RuntimeException e) {
                throw e;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
        return extractedFiles;
    }

    private long writeContentToFile(String path, String content, Path targetDirNorm,
                                    List<Path> extractedFiles, long totalBytesExtracted,
                                    Path[] currentWritingRef) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        long newTotal = totalBytesExtracted + bytes.length;
        if (newTotal > MAX_TOTAL_UNCOMPRESSED_SIZE) {
            throw new SecurityException("JSON bundle uncompressed size exceeds maximum allowed limit of 20MB");
        }
        Path resolved = validateAndResolvePath(path, targetDirNorm);
        if (resolved.getParent() != null) {
            Files.createDirectories(resolved.getParent());
        }
        currentWritingRef[0] = resolved;
        Files.write(resolved, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        extractedFiles.add(resolved);
        currentWritingRef[0] = null;
        return newTotal;
    }

    // =========================================================================
    // SINGLE JAVA FILE EXTRACTION (FALLBACK)
    // =========================================================================

    private List<Path> extractSingleJavaFile(String code, Path targetDir) throws IOException {
        Path targetDirNorm = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(targetDirNorm);

        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TOTAL_UNCOMPRESSED_SIZE) {
            throw new SecurityException("Single source file exceeds maximum size limit of 20MB");
        }

        String fileName = "Solution.java";
        Matcher matcher = PUBLIC_TYPE_PATTERN.matcher(code);
        if (matcher.find()) {
            fileName = matcher.group(1) + ".java";
        }

        Path resolved = targetDirNorm.resolve(fileName);
        Files.write(resolved, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return List.of(resolved);
    }

    // =========================================================================
    // SECURITY VALIDATOR FOR ZIP SLIP & PATH TRAVERSAL
    // =========================================================================

    public Path validateAndResolvePath(String rawEntryName, Path targetDirNorm) {
        if (rawEntryName == null || rawEntryName.isBlank()) {
            throw new SecurityException("Archive entry name cannot be null or empty");
        }

        if (rawEntryName.contains("\0")) {
            throw new SecurityException("Illegal null byte in archive entry name: " + rawEntryName);
        }

        // Convert backslashes to forward slashes for cross-platform consistency
        String sanitized = rawEntryName.replace('\\', '/').trim();

        // Reject absolute paths, leading slashes, drive letters, and UNC paths
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")
                || sanitized.matches("^[a-zA-Z]:.*") || sanitized.startsWith("//")) {
            throw new SecurityException("Illegal absolute path in archive entry: " + rawEntryName);
        }

        // Token check for ".." path components
        String[] parts = sanitized.split("/");
        for (String part : parts) {
            if ("..".equals(part.trim())) {
                throw new SecurityException("Zip Slip path traversal (..) detected in entry: " + rawEntryName);
            }
        }

        // Check for forbidden executable file types
        String lowerName = sanitized.toLowerCase();
        for (String forbidden : FORBIDDEN_EXTENSIONS) {
            if (lowerName.endsWith(forbidden)) {
                throw new SecurityException("Forbidden executable file type in archive: " + rawEntryName);
            }
        }

        Path resolved;
        try {
            resolved = targetDirNorm.resolve(sanitized).normalize().toAbsolutePath();
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid path characters in archive entry name: " + rawEntryName, e);
        }

        // Airtight prefix check
        if (!resolved.startsWith(targetDirNorm)) {
            throw new SecurityException("Zip Slip detected! Entry [" + rawEntryName
                    + "] resolves outside target directory [" + targetDirNorm + "]");
        }

        if (resolved.equals(targetDirNorm)) {
            throw new SecurityException("Archive entry resolves to target directory itself: " + rawEntryName);
        }

        return resolved;
    }

    private boolean isBase64Zip(String text) {
        if (text.startsWith(BASE64_ZIP_PREFIX)) {
            return true;
        }
        if (text.length() >= 8 && text.length() % 4 == 0 && text.matches("^[A-Za-z0-9+/=\\r\\n]+$")) {
            try {
                byte[] sample = Base64.getDecoder().decode(text.substring(0, Math.min(text.length(), 64)));
                if (sample.length >= 4) {
                    return (sample[0] == ZIP_MAGIC[0] && sample[1] == ZIP_MAGIC[1]
                            && sample[2] == ZIP_MAGIC[2] && sample[3] == ZIP_MAGIC[3])
                            || (sample[0] == EMPTY_ZIP_MAGIC[0] && sample[1] == EMPTY_ZIP_MAGIC[1]
                            && sample[2] == EMPTY_ZIP_MAGIC[2] && sample[3] == EMPTY_ZIP_MAGIC[3]);
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean isIgnoredMetadata(String rawName) {
        if (rawName == null) return false;
        String normalized = rawName.replace('\\', '/');
        return normalized.startsWith("__MACOSX/") || normalized.contains("/__MACOSX/")
                || normalized.endsWith(".DS_Store") || normalized.equals(".DS_Store");
    }

    private void cleanup(List<Path> files) {
        for (Path p : files) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
            }
        }
    }
}
