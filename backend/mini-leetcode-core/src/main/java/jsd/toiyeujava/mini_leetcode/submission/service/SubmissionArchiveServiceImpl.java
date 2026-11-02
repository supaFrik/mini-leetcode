package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class SubmissionArchiveServiceImpl implements SubmissionArchiveService {

    private static final int BUFFER_SIZE = 8192;

    private static final Pattern DELIMITED_HEADER_PATTERN =
            Pattern.compile("(?m)^[ \\t]*//\\s*(?:===|---|###)?\\s*FILE:\\s*([^\\r\\n]+?)\\s*(?:===|---|###)?\\s*$");

    private static final Pattern PUBLIC_TYPE_PATTERN =
            Pattern.compile("public\\s+(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");

    private final StorageService storageService;

    @Autowired
    public SubmissionArchiveServiceImpl(StorageService storageService) {
        this.storageService = storageService;
    }

    public SubmissionArchiveServiceImpl() {
        this(null);
    }

    @Override
    public SubmissionArchiveResult packageFiles(List<SubmissionFileDto> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Submission contains no files");
        }
        if (files.size() > SourcePathValidator.MAX_ENTRIES) {
            throw new InvalidSubmissionPathException("Submission exceeds maximum entry count of " + SourcePathValidator.MAX_ENTRIES);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long totalUncompressedSize = 0;
        List<String> entryPaths = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (SubmissionFileDto file : files) {
                if (file == null || file.path() == null) {
                    throw new InvalidSubmissionPathException("File entry or path cannot be null");
                }
                String sanitized = SourcePathValidator.sanitizeAndValidatePath(file.path());
                if (!seenPaths.add(sanitized)) {
                    throw new InvalidSubmissionPathException("Duplicate entry path in submission: " + sanitized);
                }

                byte[] bytes = file.content() != null ? file.content().getBytes(StandardCharsets.UTF_8) : new byte[0];

                if (bytes.length > SourcePathValidator.MAX_SINGLE_FILE_SIZE) {
                    throw new PayloadTooLargeException("File [" + sanitized + "] exceeds max size limit of 20MB");
                }
                totalUncompressedSize += bytes.length;
                if (totalUncompressedSize > SourcePathValidator.MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    throw new PayloadTooLargeException("Submission uncompressed size exceeds maximum allowed limit of 20MB");
                }

                ZipEntry entry = new ZipEntry(sanitized);
                entry.setTime(0);
                zos.putNextEntry(entry);
                zos.write(bytes);
                zos.closeEntry();
                entryPaths.add(sanitized);
            }
            zos.finish();
        } catch (IOException e) {
            throw new RuntimeException("Failed to construct submission ZIP archive", e);
        }

        return new SubmissionArchiveResult(baos.toByteArray(), entryPaths, totalUncompressedSize, files.size());
    }

    @Override
    public SubmissionArchiveResult sanitizeAndRepackageZip(InputStream rawZipStream) {
        if (rawZipStream == null) {
            throw new IllegalArgumentException("Archive input stream cannot be null");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long totalUncompressedSize = 0;
        int entryCount = 0;
        List<String> entryPaths = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(rawZipStream);
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (SourcePathValidator.isIgnoredMetadata(rawName)) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory() || rawName.endsWith("/") || rawName.endsWith("\\")) {
                    zis.closeEntry();
                    continue;
                }

                entryCount++;
                if (entryCount > SourcePathValidator.MAX_ENTRIES) {
                    throw new InvalidSubmissionPathException("Archive exceeds maximum entry count of " + SourcePathValidator.MAX_ENTRIES);
                }

                String sanitized = SourcePathValidator.sanitizeAndValidatePath(rawName);
                if (!seenPaths.add(sanitized)) {
                    throw new InvalidSubmissionPathException("Duplicate entry in archive: " + rawName);
                }

                ZipEntry cleanEntry = new ZipEntry(sanitized);
                cleanEntry.setTime(0);
                zos.putNextEntry(cleanEntry);

                long fileBytes = 0;
                int len;
                while ((len = zis.read(buffer)) != -1) {
                    fileBytes += len;
                    totalUncompressedSize += len;
                    if (fileBytes > SourcePathValidator.MAX_SINGLE_FILE_SIZE) {
                        throw new PayloadTooLargeException("File [" + rawName + "] exceeds max size limit of 20MB");
                    }
                    if (totalUncompressedSize > SourcePathValidator.MAX_TOTAL_UNCOMPRESSED_SIZE) {
                        throw new PayloadTooLargeException("Archive uncompressed size exceeds maximum allowed limit of 20MB");
                    }
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                entryPaths.add(sanitized);
                zis.closeEntry();
            }
            zos.finish();
        } catch (IOException e) {
            throw new RuntimeException("Failed to sanitize and repackage ZIP archive", e);
        }

        if (entryPaths.isEmpty()) {
            throw new IllegalArgumentException("Archive contains no regular files to process");
        }

        return new SubmissionArchiveResult(baos.toByteArray(), entryPaths, totalUncompressedSize, entryPaths.size());
    }

    @Override
    public SubmissionArchiveResult packageDelimitedCode(String sourceCode, SubmissionLanguage language) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code cannot be empty");
        }

        List<SubmissionFileDto> files = parseDelimitedSource(sourceCode.trim(), language);
        return packageFiles(files);
    }

    @Override
    public SubmissionArchiveResult packageAndValidate(
            List<SubmissionFileDto> files,
            InputStream rawZipStream,
            String delimitedCode,
            SubmissionLanguage language
    ) {
        if (rawZipStream != null) {
            return sanitizeAndRepackageZip(rawZipStream);
        }
        if (files != null && !files.isEmpty()) {
            return packageFiles(files);
        }
        if (delimitedCode != null && !delimitedCode.isBlank()) {
            return packageDelimitedCode(delimitedCode, language);
        }
        throw new IllegalArgumentException("No valid submission content provided");
    }

    @Override
    public byte[] generateStarterZip(String problemSlug, String problemTitle, String description, List<SubmissionFileDto> templates) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. Synthesize and write README.md
            String readme = buildScaffoldingReadme(problemSlug, problemTitle, description);
            ZipEntry readmeEntry = new ZipEntry("README.md");
            readmeEntry.setTime(0);
            zos.putNextEntry(readmeEntry);
            zos.write(readme.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. Write starter template files
            if (templates != null) {
                for (SubmissionFileDto template : templates) {
                    if (template == null || template.path() == null || template.path().isBlank()) {
                        continue;
                    }
                    String sanitized = SourcePathValidator.sanitizeAndValidatePath(template.path());
                    ZipEntry fileEntry = new ZipEntry(sanitized);
                    fileEntry.setTime(0);
                    zos.putNextEntry(fileEntry);
                    if (template.content() != null) {
                        zos.write(template.content().getBytes(StandardCharsets.UTF_8));
                    }
                    zos.closeEntry();
                }
            }
            zos.finish();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate starter scaffolding ZIP", e);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] generateStarterZip(Problem problem, List<SubmissionFileDto> starterTemplates) {
        String title = problem != null && problem.getTitle() != null ? problem.getTitle() : "Problem";
        String slug = problem != null && problem.getSlug() != null ? problem.getSlug() : "starter";
        String desc = problem != null && problem.getDescription() != null ? problem.getDescription() : "No description provided.";
        return generateStarterZip(slug, title, desc, starterTemplates);
    }

    @Override
    public String uploadSubmissionArchive(Long problemId, Long userId, byte[] zipBytes) {
        if (storageService == null) {
            throw new IllegalStateException("StorageService is not configured");
        }
        String key = storageService.generateSubmissionZipKey(problemId, userId);
        return storageService.uploadFile(
                storageService.getSubmissionsBucketName(),
                key,
                new ByteArrayInputStream(zipBytes),
                zipBytes.length,
                "application/zip"
        );
    }

    private List<SubmissionFileDto> parseDelimitedSource(String text, SubmissionLanguage language) {
        Matcher matcher = DELIMITED_HEADER_PATTERN.matcher(text);
        List<Integer> headerEnds = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();

        while (matcher.find()) {
            headerEnds.add(matcher.end());
            filePaths.add(matcher.group(1).trim());
        }

        if (filePaths.isEmpty()) {
            String fileName = "Solution.java";
            if (language == null || language == SubmissionLanguage.JAVA) {
                Matcher classMatcher = PUBLIC_TYPE_PATTERN.matcher(text);
                if (classMatcher.find()) {
                    fileName = classMatcher.group(1) + ".java";
                }
            } else if (language == SubmissionLanguage.PYTHON) {
                fileName = "solution.py";
            } else if (language == SubmissionLanguage.CPP) {
                fileName = "solution.cpp";
            }
            return List.of(new SubmissionFileDto(fileName, text));
        }

        if (filePaths.size() > SourcePathValidator.MAX_ENTRIES) {
            throw new InvalidSubmissionPathException("Submission exceeds maximum entry count of " + SourcePathValidator.MAX_ENTRIES);
        }

        List<SubmissionFileDto> files = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) {
            String path = filePaths.get(i);
            int start = headerEnds.get(i);
            int end = (i + 1 < filePaths.size()) ? findHeaderStart(text, i + 1) : text.length();

            String content = text.substring(start, end);
            if (content.startsWith("\r\n")) {
                content = content.substring(2);
            } else if (content.startsWith("\n")) {
                content = content.substring(1);
            }
            files.add(new SubmissionFileDto(path, content));
        }
        return files;
    }

    private int findHeaderStart(String bundleText, int targetIndex) {
        Matcher matcher = DELIMITED_HEADER_PATTERN.matcher(bundleText);
        int count = 0;
        while (matcher.find()) {
            if (count == targetIndex) {
                return matcher.start();
            }
            count++;
        }
        return bundleText.length();
    }

    private String buildScaffoldingReadme(String slug, String title, String description) {
        String safeTitle = (title != null && !title.isBlank()) ? title : (slug != null ? slug : "Problem");
        String safeSlug = (slug != null && !slug.isBlank()) ? slug : "problem";
        String safeDesc = (description != null && !description.isBlank()) ? description : "No description provided.";

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(safeTitle).append("\n\n");
        sb.append("- **Problem Slug**: `").append(safeSlug).append("`\n\n");
        sb.append("---\n\n");
        sb.append("## Problem Description\n");

        String descBody = safeDesc;
        if (descBody.startsWith("# " + safeTitle)) {
            descBody = descBody.substring(("# " + safeTitle).length()).trim();
        }
        sb.append(descBody).append("\n\n");
        sb.append("---\n\n");
        sb.append("## Project Structure & Guidelines\n");
        sb.append("This starter archive contains the skeleton files for solving the problem offline in your preferred IDE (IntelliJ IDEA, VS Code, Eclipse).\n\n");
        sb.append("### How to Run Locally\n");
        sb.append("1. Make sure you have JDK 17+ installed.\n");
        sb.append("2. Implement the required classes according to the specifications.\n");
        sb.append("3. Maintain the directory hierarchy and package declarations.\n\n");
        sb.append("### How to Submit\n");
        sb.append("- Package your solution files into a `.zip` file (excluding build artifacts like `target/` or `.class` files) and upload via the Mini LeetCode portal.\n");
        sb.append("- Alternatively, copy and paste the code files into the Web Multi-File Monaco Editor.\n");
        return sb.toString();
    }
}
