package jsd.toiyeujava.minileetcode.plugin.workspace;

import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Pipeline service orchestrating student workspace creation and stage setup (PL-3, PL-4).
 * <p>
 * Pipeline Execution Stages:
 * <ol>
 *   <li><b>Target Directory:</b> Ensures target directory exists on disk.</li>
 *   <li><b>POM Generation:</b> Scaffolds {@code pom.xml} from {@code templates/pom.xml} if not already present.</li>
 *   <li><b>Project Manifest:</b> Writes {@code .mlc/project.yaml} with plan, item, stage, and problem metadata.</li>
 *   <li><b>Starter Extraction:</b> Downloads starter archive from {@code ApiClient} and extracts safely via {@code ArchiveExtractor} (Zip Slip defense and non-overwrite policy).</li>
 *   <li><b>Problem Statement:</b> Downloads problem description from {@code ApiClient} and writes {@code stages/stage-N.md} in UTF-8.</li>
 *   <li><b>IDE Project Opener:</b> Invokes {@code ProjectOpener.openProject(targetDir)}.</li>
 *   <li><b>Result Assembly:</b> Returns {@link WorkspaceResult} containing generated paths and extraction statistics.</li>
 * </ol>
 */
public class WorkspaceService {

    public static final String TEMPLATE_POM_RESOURCE = "templates/pom.xml";
    public static final String MLC_DIR_NAME = ".mlc";
    public static final String MANIFEST_FILE_NAME = "project.yaml";
    public static final String STAGES_DIR_NAME = "stages";
    public static final String POM_FILE_NAME = "pom.xml";

    private final ApiClient apiClient;
    private final ArchiveExtractor archiveExtractor;
    private final ProjectOpener projectOpener;

    /**
     * Production constructor using default {@link ArchiveExtractor} and IntelliJ {@link ProjectOpener#DEFAULT}.
     *
     * @param apiClient API client for backend communication
     */
    public WorkspaceService(ApiClient apiClient) {
        this(apiClient, new ArchiveExtractor(), ProjectOpener.DEFAULT);
    }

    /**
     * Constructor allowing custom {@link ProjectOpener} (ideal for UI actions with customized project hooks).
     *
     * @param apiClient     API client for backend communication
     * @param projectOpener project opening delegate
     */
    public WorkspaceService(ApiClient apiClient, ProjectOpener projectOpener) {
        this(apiClient, new ArchiveExtractor(), projectOpener);
    }

    /**
     * Full dependency injection constructor (ideal for testing).
     *
     * @param apiClient        API client for backend communication
     * @param archiveExtractor safe archive extraction delegate
     * @param projectOpener    project opening delegate
     */
    public WorkspaceService(ApiClient apiClient, ArchiveExtractor archiveExtractor, ProjectOpener projectOpener) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.archiveExtractor = Objects.requireNonNull(archiveExtractor, "archiveExtractor must not be null");
        this.projectOpener = Objects.requireNonNull(projectOpener, "projectOpener must not be null");
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public ArchiveExtractor getArchiveExtractor() {
        return archiveExtractor;
    }

    public ProjectOpener getProjectOpener() {
        return projectOpener;
    }

    /**
     * Executes the complete project initialization pipeline for the given study plan stage.
     *
     * @param request workspace initialization parameters
     * @return result record containing generated file paths and extraction statistics
     * @throws WorkspaceException if file IO, template resolution, network, or platform errors occur
     * @throws Exception          if unhandled exceptions occur during project opening
     */
    public WorkspaceResult initializeProject(WorkspaceInitRequest request) throws Exception {
        Objects.requireNonNull(request, "request must not be null");
        Path targetDir = request.targetDirectory();

        // 1. Ensure target directory exists
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to create target directory: " + targetDir, e);
        }

        // 2. Generate pom.xml if not present
        ensurePomXml(targetDir, request);

        // 3. Write .mlc/project.yaml
        Path manifestPath = writeManifest(targetDir, request);

        // 4. Download and extract starter template archive
        ArchiveExtractor.ExtractionResult extractionResult = extractStarterTemplate(targetDir, request);

        // 5. Fetch problem statement and write stages/stage-N.md (PL-4)
        Path stageMarkdownPath = writeStageMarkdown(targetDir, request);

        // 6. Invoke ProjectOpener (PL-3)
        try {
            projectOpener.openProject(targetDir);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to open project in IDE at: " + targetDir + " - " + e.getMessage(), e);
        }

        // 7. Return assembled result
        return new WorkspaceResult(targetDir, manifestPath, stageMarkdownPath, extractionResult);
    }

    private void ensurePomXml(Path targetDir, WorkspaceInitRequest request) {
        Path pomPath = targetDir.resolve(POM_FILE_NAME);
        if (Files.exists(pomPath)) {
            // Already present, do not overwrite (preserves student customizations)
            return;
        }

        String rawTemplate = loadPomTemplate();
        String artifactId = sanitizeArtifactId(request.problemSlug(), targetDir);
        String resolvedPom = rawTemplate
                .replace("${artifactId}", artifactId)
                .replace("${projectName}", artifactId)
                .replace("${name}", artifactId);

        try {
            Files.writeString(pomPath, resolvedPom, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to write pom.xml at: " + pomPath, e);
        }
    }

    private String loadPomTemplate() {
        ClassLoader cl = getClass().getClassLoader();
        InputStream is = cl.getResourceAsStream(TEMPLATE_POM_RESOURCE);
        if (is == null) {
            is = getClass().getResourceAsStream("/" + TEMPLATE_POM_RESOURCE);
        }
        if (is == null) {
            throw new WorkspaceException("Classpath template resource not found: " + TEMPLATE_POM_RESOURCE);
        }
        try (InputStream in = is) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to read classpath template resource: " + TEMPLATE_POM_RESOURCE, e);
        }
    }

    private String sanitizeArtifactId(String problemSlug, Path targetDir) {
        String base = (problemSlug != null && !problemSlug.isBlank())
                ? problemSlug
                : (targetDir.getFileName() != null ? targetDir.getFileName().toString() : "student-project");
        String sanitized = base.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        return sanitized.isEmpty() ? "student-project" : sanitized;
    }

    private Path writeManifest(Path targetDir, WorkspaceInitRequest request) {
        Path mlcDir = targetDir.resolve(MLC_DIR_NAME);
        try {
            Files.createDirectories(mlcDir);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to create metadata directory: " + mlcDir, e);
        }

        Path manifestPath = mlcDir.resolve(MANIFEST_FILE_NAME);
        ProjectManifest manifest = new ProjectManifest(
                request.planSlug(),
                request.itemSlugOrId(),
                request.stageNumber(),
                request.problemSlug(),
                request.problemId()
        );

        try {
            manifest.writeTo(manifestPath);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to write project manifest at: " + manifestPath, e);
        }
        return manifestPath;
    }

    private ArchiveExtractor.ExtractionResult extractStarterTemplate(Path targetDir, WorkspaceInitRequest request) {
        byte[] zipBytes;
        try {
            zipBytes = apiClient.getStarterTemplate(request.problemSlug());
        } catch (AuthenticationException ae) {
            throw new WorkspaceException("Authentication failed while fetching starter template (PAT invalid/expired): " + ae.getMessage(), ae);
        } catch (MiniLeetCodeApiException apiEx) {
            throw new WorkspaceException("API error while fetching starter template for '" + request.problemSlug() + "': " + apiEx.getMessage(), apiEx);
        } catch (Exception e) {
            throw new WorkspaceException("Network/client error while fetching starter template for '" + request.problemSlug() + "': " + e.getMessage(), e);
        }

        if (zipBytes == null || zipBytes.length == 0) {
            return new ArchiveExtractor.ExtractionResult(0, 0, java.util.List.of());
        }

        try {
            return archiveExtractor.extract(zipBytes, targetDir);
        } catch (SecurityException se) {
            throw new WorkspaceException("Archive security violation (Zip Slip detected) in starter template for problem '"
                    + request.problemSlug() + "': " + se.getMessage(), se);
        } catch (IOException ioe) {
            throw new WorkspaceException("Failed to extract starter template archive into " + targetDir + ": " + ioe.getMessage(), ioe);
        }
    }

    private Path writeStageMarkdown(Path targetDir, WorkspaceInitRequest request) {
        Path stagesDir = targetDir.resolve(STAGES_DIR_NAME);
        try {
            Files.createDirectories(stagesDir);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to create stages directory: " + stagesDir, e);
        }

        Path stageMarkdownPath = stagesDir.resolve("stage-" + request.stageNumber() + ".md");

        ProblemDto problem;
        try {
            problem = apiClient.getProblem(request.problemSlug());
        } catch (AuthenticationException ae) {
            throw new WorkspaceException("Authentication failed while fetching problem description (PAT invalid/expired): " + ae.getMessage(), ae);
        } catch (MiniLeetCodeApiException apiEx) {
            throw new WorkspaceException("API error while fetching problem description for '" + request.problemSlug() + "': " + apiEx.getMessage(), apiEx);
        } catch (Exception e) {
            throw new WorkspaceException("Network/client error while fetching problem description for '" + request.problemSlug() + "': " + e.getMessage(), e);
        }

        String markdownContent;
        if (problem != null && problem.description() != null && !problem.description().isBlank()) {
            markdownContent = problem.description();
        } else {
            String title = (problem != null && problem.title() != null && !problem.title().isBlank())
                    ? problem.title()
                    : "Stage " + request.stageNumber();
            markdownContent = "# " + title + "\n\nNo description provided.\n";
        }

        try {
            Files.writeString(stageMarkdownPath, markdownContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new WorkspaceException("Failed to write stage description at: " + stageMarkdownPath, e);
        }

        return stageMarkdownPath;
    }
}
