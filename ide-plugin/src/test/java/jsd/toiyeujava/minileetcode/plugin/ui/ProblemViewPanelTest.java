package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProblemViewPanelTest {

    @TempDir
    Path tempDir;

    private Project project;
    private ProblemViewPanel panel;

    @BeforeEach
    void setUp() {
        project = createProject(tempDir);
        panel = new ProblemViewPanel(project, null);
    }

    private Project createProject(Path projectDir) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[]{Project.class},
                (proxy, method, args) -> {
                    if ("getBasePath".equals(method.getName())) {
                        return projectDir.toString();
                    }
                    if ("isDisposed".equals(method.getName())) {
                        return false;
                    }
                    if ("getUserData".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );
    }

    @Test
    void testInitialState_ActionsDisabled() {
        assertFalse(panel.getRunButton().isEnabled(), "Run button must be disabled initially");
        assertFalse(panel.getSubmitButton().isEnabled(), "Submit button must be disabled initially");
        assertFalse(panel.getNextStageButton().isVisible(), "Next stage button must be hidden initially");
    }

    @Test
    void testLockedStage_ActionsDisabledWithTooltip() {
        StageDto stageDto = new StageDto(2, 141L, "oop-wallet", "Wallet", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.LOCKED);
        StageTreeNode stageNode = new StageTreeNode(stageDto);
        ProblemDto problemDto = new ProblemDto(141L, "Wallet", "oop-wallet", "Statement");

        panel.setStage(stageNode, problemDto);

        assertFalse(panel.getRunButton().isEnabled(), "Run button must be disabled for locked stage");
        assertFalse(panel.getSubmitButton().isEnabled(), "Submit button must be disabled for locked stage");
        assertTrue(panel.getRunButton().getToolTipText().contains("locked"), "Tooltip must explain stage is locked");
    }

    @Test
    void testMismatchedProject_ActionsDisabledWithTooltip() throws IOException {
        ProjectManifest manifest = ProjectManifest.of("oop", "item", 1, "problem-A", 100L);
        manifest.writeToProject(tempDir);

        StageDto stageDto = new StageDto(1, 200L, "problem-B", "Problem B", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        StageTreeNode stageNode = new StageTreeNode(stageDto);
        ProblemDto problemDto = new ProblemDto(200L, "Problem B", "problem-B", "Statement");

        panel.setStage(stageNode, problemDto);

        assertFalse(panel.getRunButton().isEnabled(), "Run button must be disabled when project does not match");
        assertFalse(panel.getSubmitButton().isEnabled(), "Submit button must be disabled when project does not match");
        assertTrue(panel.getRunButton().getToolTipText().contains("does not match this stage"), "Tooltip must indicate mismatch");
    }

    @Test
    void testMatchingProject_ActionsEnabled() throws IOException {
        Path matchingDir = tempDir.resolve("oop-employee-payroll-encapsulation");
        Files.createDirectories(matchingDir);
        ProjectManifest manifest = ProjectManifest.of("oop", "item", 1, "oop-employee-payroll-encapsulation", 140L);
        manifest.writeToProject(matchingDir);

        Project matchingProject = createProject(matchingDir);
        ProblemViewPanel matchingPanel = new ProblemViewPanel(matchingProject, null);

        StageDto stageDto = new StageDto(1, 140L, "oop-employee-payroll-encapsulation", "Employee Payroll", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        StageTreeNode stageNode = new StageTreeNode(stageDto);
        ProblemDto problemDto = new ProblemDto(140L, "Employee Payroll", "oop-employee-payroll-encapsulation", "Statement");

        matchingPanel.setStage(stageNode, problemDto);

        assertTrue(matchingPanel.getRunButton().isEnabled(), "Run button must be enabled when project matches");
        assertTrue(matchingPanel.getSubmitButton().isEnabled(), "Submit button must be enabled when project matches");
    }

    @Test
    void testMismatchedDirectoryName_ActionsDisabled() throws IOException {
        Path mismatchedDir = tempDir.resolve("test-plugin");
        Files.createDirectories(mismatchedDir);
        ProjectManifest manifest = ProjectManifest.of("oop", "item", 1, "oop-employee-payroll-encapsulation", 140L);
        manifest.writeToProject(mismatchedDir);

        Project mismatchedProject = createProject(mismatchedDir);
        ProblemViewPanel mismatchedPanel = new ProblemViewPanel(mismatchedProject, null);

        StageDto stageDto = new StageDto(1, 140L, "oop-employee-payroll-encapsulation", "Employee Payroll", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        StageTreeNode stageNode = new StageTreeNode(stageDto);
        ProblemDto problemDto = new ProblemDto(140L, "Employee Payroll", "oop-employee-payroll-encapsulation", "Statement");

        mismatchedPanel.setStage(stageNode, problemDto);

        assertFalse(mismatchedPanel.getRunButton().isEnabled(), "Run button must be disabled when folder name does not match stage slug");
        assertFalse(mismatchedPanel.getSubmitButton().isEnabled(), "Submit button must be disabled when folder name does not match stage slug");
        assertTrue(mismatchedPanel.getRunButton().getToolTipText().contains("does not match"), "Tooltip must explain directory mismatch");
    }

    @Test
    void testRootWorkspaceFolder_ActionsDisabled() throws IOException {
        Path rootWorkspaceDir = tempDir.resolve("Mini Leetcode");
        Files.createDirectories(rootWorkspaceDir);

        Project rootProject = createProject(rootWorkspaceDir);
        ProblemViewPanel rootPanel = new ProblemViewPanel(rootProject, null);

        StageDto stageDto = new StageDto(1, 140L, "oop-employee-payroll-encapsulation", "Employee Payroll", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        StageTreeNode stageNode = new StageTreeNode(stageDto);
        ProblemDto problemDto = new ProblemDto(140L, "Employee Payroll", "oop-employee-payroll-encapsulation", "Statement");

        rootPanel.setStage(stageNode, problemDto);

        assertFalse(rootPanel.getRunButton().isEnabled(), "Run button must be disabled for root workspace folder");
        assertFalse(rootPanel.getSubmitButton().isEnabled(), "Submit button must be disabled for root workspace folder");
        assertTrue(rootPanel.getRunButton().getToolTipText().contains("does not match"), "Tooltip must explain directory mismatch");
    }
}
