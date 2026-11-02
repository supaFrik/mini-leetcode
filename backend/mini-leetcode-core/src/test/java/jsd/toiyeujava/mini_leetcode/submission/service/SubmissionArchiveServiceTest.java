package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionArchiveServiceTest {

    private SubmissionArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new SubmissionArchiveServiceImpl();
    }

    @Test
    @DisplayName("FUNC-01: packageFiles should package valid JSON files array into clean ZIP")
    void packageFiles_ValidJsonList_CreatesZipArchive() throws IOException {
        List<SubmissionFileDto> files = List.of(
                new SubmissionFileDto("model/Shape.java", "package model; public interface Shape { double area(); }"),
                new SubmissionFileDto("model/Circle.java", "package model; public class Circle implements Shape { public double area(){return 0;} }"),
                new SubmissionFileDto("Main.java", "public class Main {}")
        );

        SubmissionArchiveResult result = archiveService.packageFiles(files);

        assertThat(result).isNotNull();
        assertThat(result.zipBytes()).isNotEmpty();
        assertThat(result.entryCount()).isEqualTo(3);
        assertThat(result.entryPaths()).containsExactlyInAnyOrder("model/Shape.java", "model/Circle.java", "Main.java");

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactlyInAnyOrder("model/Shape.java", "model/Circle.java", "Main.java");
    }

    @Test
    @DisplayName("FUNC-02: packageFiles should preserve nested package hierarchies")
    void packageFiles_NestedPackageHierarchy_PreservesStructure() throws IOException {
        List<SubmissionFileDto> files = List.of(
                new SubmissionFileDto("com/example/service/algorithm/GraphSolver.java", "package com.example.service.algorithm; public class GraphSolver {}")
        );

        SubmissionArchiveResult result = archiveService.packageFiles(files);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactly("com/example/service/algorithm/GraphSolver.java");
    }

    @Test
    @DisplayName("FUNC-03: packageDelimited should parse standard markers and package into clean ZIP")
    void packageDelimited_StandardMarkers_ParsesAndPackages() throws IOException {
        String delimited = """
                // === FILE: A.java ===
                public class A {}

                // === FILE: B.java ===
                public class B {}
                """;

        SubmissionArchiveResult result = archiveService.packageDelimitedCode(delimited, SubmissionLanguage.JAVA);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactlyInAnyOrder("A.java", "B.java");
    }

    @Test
    @DisplayName("FUNC-04: packageDelimited should parse whitespace and fence variations")
    void packageDelimited_WhitespaceAndFenceVariations_ParsesSuccessfully() throws IOException {
        String delimited = """
                   //   ===   FILE:   com/example/Test1.java   ===   
                package com.example;
                public class Test1 {}
                
                // --- FILE: com/example/Test2.java ---
                package com.example;
                public class Test2 {}
                
                // FILE: Test3.java
                public class Test3 {}
                """;

        SubmissionArchiveResult result = archiveService.packageDelimited(delimited);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactlyInAnyOrder(
                "com/example/Test1.java",
                "com/example/Test2.java",
                "Test3.java"
        );
    }

    @Test
    @DisplayName("FUNC-05: packageDelimited should parse markers missing closing fence")
    void packageDelimited_MissingClosingFence_ParsesSuccessfully() throws IOException {
        String delimited = """
                // === FILE: com/example/Solution.java
                package com.example;
                public class Solution {}
                """;

        SubmissionArchiveResult result = archiveService.packageDelimited(delimited);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactly("com/example/Solution.java");
    }

    @Test
    @DisplayName("FUNC-06: packageSingleSource should derive public class name")
    void packageSingleSource_DerivesPublicClassName() throws IOException {
        String code = """
                package com.demo;
                public class Calculator {
                    public int add(int a, int b) { return a + b; }
                }
                """;

        SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactly("Calculator.java");
    }

    @Test
    @DisplayName("FUNC-07: packageSingleSource without public class should default to Solution.java")
    void packageSingleSource_NoPublicClass_DefaultsToSolutionJava() throws IOException {
        String code = """
                class InternalSolution {
                    int compute() { return 42; }
                }
                """;

        SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);

        List<String> entryNames = readZipEntries(result.zipBytes());
        assertThat(entryNames).containsExactly("Solution.java");
    }

    @Test
    @DisplayName("FUNC-08: validateAndSanitizeZip should repackage clean ZIP stream deterministically")
    void validateAndSanitizeZip_ValidZipStream_RepackagesDeterministically() throws IOException {
        byte[] rawZip = createTestZip(List.of(
                new TestEntry("src/Foo.java", "public class Foo {}".getBytes(StandardCharsets.UTF_8)),
                new TestEntry("src/Bar.java", "public class Bar {}".getBytes(StandardCharsets.UTF_8))
        ));

        SubmissionArchiveResult result = archiveService.validateAndSanitizeZip(new ByteArrayInputStream(rawZip));

        List<String> entries = readZipEntries(result.zipBytes());
        assertThat(entries).containsExactlyInAnyOrder("src/Foo.java", "src/Bar.java");
    }

    @Test
    @DisplayName("FUNC-09: validateAndSanitizeZip should silently filter __MACOSX and .DS_Store metadata")
    void validateAndSanitizeZip_MacOsMetadata_SafelyFiltersMetadata() throws IOException {
        byte[] rawZip = createTestZip(List.of(
                new TestEntry("__MACOSX/._Solution.java", "METADATA".getBytes(StandardCharsets.UTF_8)),
                new TestEntry(".DS_Store", "STORE".getBytes(StandardCharsets.UTF_8)),
                new TestEntry("Solution.java", "public class Solution {}".getBytes(StandardCharsets.UTF_8))
        ));

        SubmissionArchiveResult result = archiveService.validateAndSanitizeZip(new ByteArrayInputStream(rawZip));

        List<String> entries = readZipEntries(result.zipBytes());
        assertThat(entries).containsExactly("Solution.java");
    }

    @Test
    @DisplayName("FUNC-10: generateStarterZip should synthesize README.md and bundle template files")
    void generateStarterZip_ValidProblem_GeneratesZipWithReadme() throws IOException {
        List<SubmissionFileDto> templates = List.of(
                new SubmissionFileDto("src/Solution.java", "public class Solution {}"),
                new SubmissionFileDto("src/Helper.java", "public class Helper {}")
        );

        byte[] starterZip = archiveService.generateStarterZip("two-sum", "Two Sum", "# Two Sum\nFind two numbers...", templates);

        List<String> entries = readZipEntries(starterZip);
        assertThat(entries).contains("README.md", "src/Solution.java", "src/Helper.java");

        String readmeContent = readZipEntryContent(starterZip, "README.md");
        assertThat(readmeContent).contains("Two Sum", "two-sum", "How to Run Locally");
    }

    @Test
    @DisplayName("FUNC-11: generateStarterZip with empty templates should generate zip with README.md only")
    void generateStarterZip_EmptyTemplates_GeneratesZipWithReadmeOnly() throws IOException {
        byte[] starterZip = archiveService.generateStarterZip("blank-exercise", "Blank Exercise", "No templates", List.of());

        List<String> entries = readZipEntries(starterZip);
        assertThat(entries).containsExactly("README.md");
    }

    @Test
    @DisplayName("FUNC-12: generateStarterZip from Problem entity creates valid archive")
    void generateStarterZip_FromProblemEntity_CreatesValidArchive() throws IOException {
        Problem problem = Problem.builder()
                .id(1L)
                .title("OOP Bank System")
                .slug("oop-bank-system")
                .difficulty(Difficulty.MEDIUM)
                .description("Design a thread-safe bank account system.")
                .build();

        List<SubmissionFileDto> templates = List.of(
                new SubmissionFileDto("BankAccount.java", "public interface BankAccount {}")
        );

        byte[] starterZip = archiveService.generateStarterZip(problem, templates);

        List<String> entries = readZipEntries(starterZip);
        assertThat(entries).containsExactlyInAnyOrder("README.md", "BankAccount.java");
    }

    @Test
    @DisplayName("FUNC-13: packageAndValidate dispatches to proper ingestion channel")
    void packageAndValidate_DispatchesCorrectly() throws IOException {
        // Channel 1: rawZipStream
        byte[] rawZip = createTestZip(List.of(new TestEntry("Main.java", "code".getBytes(StandardCharsets.UTF_8))));
        SubmissionArchiveResult r1 = archiveService.packageAndValidate(null, new ByteArrayInputStream(rawZip), null, null);
        assertThat(r1.entryPaths()).containsExactly("Main.java");

        // Channel 2: files list
        SubmissionArchiveResult r2 = archiveService.packageAndValidate(List.of(new SubmissionFileDto("A.java", "a")), null, null, null);
        assertThat(r2.entryPaths()).containsExactly("A.java");

        // Channel 3: delimited code
        SubmissionArchiveResult r3 = archiveService.packageAndValidate(null, null, "public class B {}", SubmissionLanguage.JAVA);
        assertThat(r3.entryPaths()).containsExactly("B.java");

        // Channel 4: empty inputs
        assertThatThrownBy(() -> archiveService.packageAndValidate(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Helper Methods ---
    private List<String> readZipEntries(byte[] zipBytes) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }

    private String readZipEntryContent(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toString(StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    private byte[] createTestZip(List<TestEntry> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (TestEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.name()));
                zos.write(entry.content());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private record TestEntry(String name, byte[] content) {
    }
}
