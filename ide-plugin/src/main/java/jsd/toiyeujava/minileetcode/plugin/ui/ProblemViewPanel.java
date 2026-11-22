package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import jsd.toiyeujava.minileetcode.plugin.actions.CheckAction;
import jsd.toiyeujava.minileetcode.plugin.actions.NextStageAction;
import jsd.toiyeujava.minileetcode.plugin.actions.NotificationPresenter;
import jsd.toiyeujava.minileetcode.plugin.actions.SubmitCheckHandler;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import jsd.toiyeujava.minileetcode.plugin.model.SubmissionDto;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Tab 2 of Mini LeetCode Tool Window: Problem View.
 * Displays problem statement, action toolbar (Run, Submit, Next Stage),
 * and an expandable Result Section for test feedback.
 */
public class ProblemViewPanel extends JPanel {

    private final Project project;
    private final Runnable onStagePassedCallback;

    // Header Components
    private final JLabel titleLabel = new JLabel("No stage selected");
    private final JLabel difficultyBadge = new JLabel();
    private final JLabel statusBadge = new JLabel();

    // Action Toolbar
    private final JButton runButton = new JButton("Run", AllIcons.Actions.Execute);
    private final JButton submitButton = new JButton("Submit", AllIcons.Actions.Commit);
    private final JButton nextStageButton = new JButton("Next Stage →", AllIcons.Actions.Forward);

    // Result Section
    private final JPanel resultSection = new JPanel(new BorderLayout(5, 5));
    private final JLabel resultVerdictLabel = new JLabel();
    private final JLabel resultStatsLabel = new JLabel();
    private final JBTextArea resultDetailsArea = new JBTextArea();
    private final JBScrollPane resultDetailsScrollPane = new JBScrollPane(resultDetailsArea);
    private final JButton toggleResultButton = new JButton("Hide Result");

    // Statement Container
    private final JPanel statementContainer = new JPanel(new BorderLayout());
    private JBCefBrowser cefBrowser;
    private JEditorPane fallbackEditorPane;

    // Active state
    private StageTreeNode currentStageNode;
    private ProblemDto currentProblemDto;

    public ProblemViewPanel(@NotNull Project project, @Nullable Runnable onStagePassedCallback) {
        super(new BorderLayout(0, 0));
        this.project = project;
        this.onStagePassedCallback = onStagePassedCallback;
        initUI();
    }

    private void initUI() {
        setBorder(JBUI.Borders.empty(8));

        // 1. Top Panel (Header + Action Toolbar + Result Section)
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // 1.1 Header Bar
        JPanel headerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerBar.add(titleLabel);

        difficultyBadge.setVisible(false);
        headerBar.add(difficultyBadge);

        statusBadge.setVisible(false);
        headerBar.add(statusBadge);
        topPanel.add(headerBar);

        // 1.2 Action Toolbar
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        runButton.setToolTipText("Run public sample test cases (Ctrl+Alt+R)");
        runButton.addActionListener(e -> executeRun());
        actionBar.add(runButton);

        submitButton.setToolTipText("Submit for full official grading (Ctrl+Alt+Enter)");
        submitButton.addActionListener(e -> executeSubmit());
        actionBar.add(submitButton);

        nextStageButton.setVisible(false);
        nextStageButton.setToolTipText("Advance to next stage in this project");
        nextStageButton.addActionListener(e -> executeNextStage());
        actionBar.add(nextStageButton);

        topPanel.add(actionBar);

        // 1.3 Collapsible Result Section
        setupResultSection();
        topPanel.add(resultSection);

        add(topPanel, BorderLayout.NORTH);

        // 2. Center: Statement Viewer
        setupStatementViewer();
        add(statementContainer, BorderLayout.CENTER);

        // Initial state
        setActionsEnabled(false);
        resultSection.setVisible(false);
        renderStatement(MarkdownRenderer.renderToHtml(null));
    }

    private void setupResultSection() {
        resultSection.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0),
                JBUI.Borders.empty(8)
        ));
        resultSection.setBackground(UIUtil.getPanelBackground());

        JPanel resultHeader = new JPanel(new BorderLayout(5, 5));
        resultHeader.setOpaque(false);

        JPanel labelGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        labelGroup.setOpaque(false);
        resultVerdictLabel.setFont(resultVerdictLabel.getFont().deriveFont(Font.BOLD, 13f));
        labelGroup.add(resultVerdictLabel);
        labelGroup.add(resultStatsLabel);
        resultHeader.add(labelGroup, BorderLayout.CENTER);

        toggleResultButton.setFont(toggleResultButton.getFont().deriveFont(11f));
        toggleResultButton.addActionListener(e -> {
            boolean visible = resultDetailsScrollPane.isVisible();
            resultDetailsScrollPane.setVisible(!visible);
            toggleResultButton.setText(visible ? "Show Details" : "Hide Details");
            revalidate();
            repaint();
        });
        resultHeader.add(toggleResultButton, BorderLayout.EAST);
        resultSection.add(resultHeader, BorderLayout.NORTH);

        resultDetailsArea.setEditable(false);
        resultDetailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultDetailsArea.setRows(5);
        resultDetailsScrollPane.setPreferredSize(new Dimension(300, 100));
        resultSection.add(resultDetailsScrollPane, BorderLayout.CENTER);
    }

    private void setupStatementViewer() {
        try {
            if (JBCefApp.isSupported()) {
                cefBrowser = new JBCefBrowser();
                statementContainer.add(cefBrowser.getComponent(), BorderLayout.CENTER);
                return;
            }
        } catch (Throwable ignored) {
            // Fallback to Swing HTML in headless or unsupported environments
        }

        fallbackEditorPane = new JEditorPane();
        fallbackEditorPane.setContentType("text/html");
        fallbackEditorPane.setEditable(false);
        JBScrollPane scrollPane = new JBScrollPane(fallbackEditorPane);
        statementContainer.add(scrollPane, BorderLayout.CENTER);
    }

    public void setStage(@Nullable StageTreeNode stageNode, @Nullable ProblemDto problemDto) {
        this.currentStageNode = stageNode;
        this.currentProblemDto = problemDto;

        if (stageNode == null || problemDto == null) {
            clear();
            return;
        }

        titleLabel.setText("Stage " + stageNode.getStageNo() + ": " + stageNode.getTitle());

        // Difficulty badge
        Difficulty diff = stageNode.getDifficulty();
        if (diff != null) {
            difficultyBadge.setText(diff.name());
            difficultyBadge.setForeground(getDifficultyColor(diff));
            difficultyBadge.setVisible(true);
        } else {
            difficultyBadge.setVisible(false);
        }

        // Status badge
        StageStatus status = stageNode.getStatus();
        if (status != null) {
            statusBadge.setText(status.name());
            statusBadge.setVisible(true);
        } else {
            statusBadge.setVisible(false);
        }

        // Next stage button visibility
        nextStageButton.setVisible(stageNode.isDone());

        // Check if locked or maintenance
        if (stageNode.isLocked()) {
            setActionsEnabled(false, "Stage is locked. Complete previous stages first.");
            renderStatement(MarkdownRenderer.renderToHtml(
                    "# " + stageNode.getTitle() + "\n\n> 🔒 **Stage is locked.** Please complete previous stages to unlock."
            ));
            return;
        }

        if (!stageNode.isPublished()) {
            setActionsEnabled(false, "Stage is under maintenance. Please check back later.");
            renderStatement(MarkdownRenderer.renderToHtml(
                    "# " + stageNode.getTitle() + "\n\n> ⚠️ **Stage is under maintenance.** Please check back later."
            ));
            return;
        }

        updateActionButtonsState();
        renderStatement(MarkdownRenderer.renderToHtml(problemDto.description()));
    }

    public void updateActionButtonsState() {
        if (currentStageNode == null || currentProblemDto == null) {
            setActionsEnabled(false, "No stage selected.");
            return;
        }

        if (currentStageNode.isLocked()) {
            setActionsEnabled(false, "Stage is locked. Complete previous stages first.");
            return;
        }

        if (!currentStageNode.isPublished()) {
            setActionsEnabled(false, "Stage is under maintenance. Please check back later.");
            return;
        }

        if (project == null || project.getBasePath() == null || project.isDisposed()) {
            setActionsEnabled(false, "No active project open in IntelliJ.");
            return;
        }

        Path projectRoot = Path.of(project.getBasePath());
        String dirName = projectRoot.getFileName() != null ? projectRoot.getFileName().toString() : "";
        String expectedSlug = currentStageNode.getSlug();

        // Validate condition 1: Active project root directory name matches current stage slug
        if (!dirName.equalsIgnoreCase(expectedSlug)) {
            setActionsEnabled(false, "Active project directory (" + dirName + ") does not match this stage (" + currentStageNode.getTitle() + "). Open the matching stage folder (" + expectedSlug + ") to enable execution.");
            return;
        }

        // Validate condition 2: .mlc/project.yaml manifest exists in project root
        if (!ProjectManifest.existsIn(projectRoot)) {
            setActionsEnabled(false, "Active project is not a Mini LeetCode stage. Missing .mlc/project.yaml in project root.");
            return;
        }

        // Validate condition 3: Manifest metadata matches stage
        try {
            ProjectManifest manifest = ProjectManifest.readFromProject(projectRoot);
            boolean matchesSlug = manifest.problemSlug() != null && manifest.problemSlug().equalsIgnoreCase(expectedSlug);
            boolean matchesId = manifest.problemId() != null && manifest.problemId().equals(currentStageNode.getProblemId());

            if (!matchesSlug && !matchesId) {
                String openStageInfo = manifest.problemSlug() != null ? manifest.problemSlug() : ("Stage " + manifest.currentStage());
                setActionsEnabled(false, "Active project manifest (" + openStageInfo + ") does not match this stage (" + currentStageNode.getTitle() + "). Open this stage to enable execution.");
                return;
            }
        } catch (Exception ex) {
            setActionsEnabled(false, "Cannot verify project: " + ex.getMessage());
            return;
        }

        // Active project matches stage and stage is unlocked
        runButton.setEnabled(true);
        submitButton.setEnabled(true);
        runButton.setToolTipText("Run public sample test cases (Ctrl+Alt+R)");
        submitButton.setToolTipText("Submit for full official grading (Ctrl+Alt+Enter)");
    }

    public void renderStatement(@NotNull String html) {
        if (cefBrowser != null) {
            cefBrowser.loadHTML(html);
        } else if (fallbackEditorPane != null) {
            fallbackEditorPane.setText(html);
            fallbackEditorPane.setCaretPosition(0);
        }
    }

    public void clear() {
        this.currentStageNode = null;
        this.currentProblemDto = null;
        titleLabel.setText("No stage selected");
        difficultyBadge.setVisible(false);
        statusBadge.setVisible(false);
        nextStageButton.setVisible(false);
        setActionsEnabled(false, null);
        resultSection.setVisible(false);
        renderStatement(MarkdownRenderer.renderToHtml(null));
    }

    private void setActionsEnabled(boolean enabled) {
        setActionsEnabled(enabled, null);
    }

    private void setActionsEnabled(boolean enabled, @Nullable String tooltip) {
        runButton.setEnabled(enabled);
        submitButton.setEnabled(enabled);
        if (tooltip != null) {
            runButton.setToolTipText(tooltip);
            submitButton.setToolTipText(tooltip);
        }
    }

    public JButton getRunButton() {
        return runButton;
    }

    public JButton getSubmitButton() {
        return submitButton;
    }

    public JButton getNextStageButton() {
        return nextStageButton;
    }

    public StageTreeNode getCurrentStageNode() {
        return currentStageNode;
    }

    public ProblemDto getCurrentProblemDto() {
        return currentProblemDto;
    }

    private Color getDifficultyColor(Difficulty diff) {
        return switch (diff) {
            case EASY -> new JBColor(new Color(40, 167, 69), new Color(46, 160, 67));
            case MEDIUM -> new JBColor(new Color(230, 115, 0), new Color(210, 153, 34));
            case HARD -> new JBColor(new Color(215, 58, 73), new Color(248, 81, 73));
        };
    }

    // =========================================================================
    // Execution Engine: Run & Submit
    // =========================================================================

    public void executeRun() {
        executeEvaluation(true);
    }

    public void executeSubmit() {
        executeEvaluation(false);
    }

    private void executeEvaluation(boolean isRun) {
        if (currentStageNode == null || currentProblemDto == null) {
            return;
        }

        // 1. Resolve active project directory & validate matching stage
        if (project == null || project.getBasePath() == null || project.isDisposed()) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "No active project open in IntelliJ.",
                    NotificationType.WARNING);
            return;
        }

        Path projectRoot = Path.of(project.getBasePath());
        String dirName = projectRoot.getFileName() != null ? projectRoot.getFileName().toString() : "";
        String expectedSlug = currentStageNode.getSlug();

        if (!dirName.equalsIgnoreCase(expectedSlug)) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "Active project directory (" + dirName + ") does not match stage slug (" + expectedSlug + "). Open the matching stage folder to enable execution.",
                    NotificationType.WARNING);
            return;
        }

        if (!ProjectManifest.existsIn(projectRoot)) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "Please open the Mini LeetCode project for this stage to run/submit.",
                    NotificationType.WARNING);
            return;
        }

        try {
            ProjectManifest manifest = ProjectManifest.readFromProject(projectRoot);
            boolean matchesSlug = manifest.problemSlug() != null && manifest.problemSlug().equalsIgnoreCase(expectedSlug);
            boolean matchesId = manifest.problemId() != null && manifest.problemId().equals(currentStageNode.getProblemId());
            if (!matchesSlug && !matchesId) {
                NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                        "The open project (" + manifest.problemSlug() + " Stage " + manifest.currentStage()
                                + ") does not match this stage (" + currentStageNode.getTitle() + ").",
                        NotificationType.WARNING);
                return;
            }
        } catch (Exception ex) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "Cannot read .mlc/project.yaml: " + ex.getMessage(),
                    NotificationType.ERROR);
            return;
        }
        Path projectDir = projectRoot;

        // 2. Save all open documents in IDE
        FileDocumentManager.getInstance().saveAllDocuments();

        // 3. Check and zip src/main/java
        Path srcMainJava = projectDir.resolve("src/main/java");
        if (!Files.isDirectory(srcMainJava)) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "Directory src/main/java does not exist in project.",
                    NotificationType.ERROR);
            return;
        }

        byte[] zipBytes;
        try {
            zipBytes = SubmitCheckHandler.zipSources(srcMainJava);
        } catch (Exception ex) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    ex.getMessage(),
                    NotificationType.ERROR);
            return;
        }

        // 4. Update UI to evaluating state
        showEvaluating(isRun);
        setActionsEnabled(false);

        // 5. Run async on background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String serverUrl = MiniLeetCodeSettingsState.getInstance().getNormalizedServerUrl();
                String pat = MiniLeetCodePasswordSafe.getInstance().getPassword();
                if (serverUrl == null || serverUrl.isBlank()) {
                    throw new IllegalStateException("Server URL not configured in Settings › Tools › Mini LeetCode.");
                }
                ApiClient client = new ApiClient(serverUrl, pat);

                long problemId = currentStageNode.getProblemId();
                SubmissionDto submitted = isRun ? client.runStage(problemId, zipBytes) : client.submitStage(problemId, zipBytes);

                // Poll submission
                SubmissionDto current = submitted;
                long waited = 0;
                while (!current.isFinished() && waited < 120_000) {
                    Thread.sleep(1000);
                    waited += 1000;
                    current = client.getSubmission(submitted.id());
                }

                SubmissionDto finalResult = current;
                ApplicationManager.getApplication().invokeLater(() -> {
                    showResult(finalResult, isRun);
                    updateActionButtonsState();

                    // If Submit passed (AC), unlock next stage
                    if (!isRun && finalResult.isAccepted()) {
                        nextStageButton.setVisible(true);
                        if (onStagePassedCallback != null) {
                            onStagePassedCallback.run();
                        }
                    }
                });

            } catch (AuthenticationException ae) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showError("Session expired (PAT invalid/expired). Please update token in Settings.");
                    updateActionButtonsState();
                });
            } catch (MiniLeetCodeApiException apiEx) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showError("Server error (HTTP " + apiEx.getStatusCode() + "): " + apiEx.getMessage());
                    updateActionButtonsState();
                });
            } catch (Throwable t) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    showError("Execution failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                    updateActionButtonsState();
                });
            }
        });
    }

    private void showEvaluating(boolean isRun) {
        resultSection.setVisible(true);
        resultVerdictLabel.setText(isRun ? "RUNNING SAMPLES" : "JUDGING SUBMISSION");
        resultVerdictLabel.setForeground(new JBColor(new Color(230, 115, 0), new Color(210, 153, 34)));
        resultStatsLabel.setText("Evaluating code in sandbox...");
        resultDetailsArea.setText("Please wait while the server evaluates your solution...");
        resultDetailsScrollPane.setVisible(true);
        toggleResultButton.setText("Hide Details");
        revalidate();
        repaint();
    }

    private void showResult(SubmissionDto sub, boolean isRun) {
        resultSection.setVisible(true);

        boolean ac = sub.isAccepted();
        String verdict = sub.verdict() != null ? sub.verdict() : sub.status();

        Color verdictColor = ac
                ? new JBColor(new Color(40, 167, 69), new Color(46, 160, 67))
                : new JBColor(new Color(215, 58, 73), new Color(248, 81, 73));

        resultVerdictLabel.setText(ac ? "ACCEPTED" : verdict);
        resultVerdictLabel.setForeground(verdictColor);

        // Stats summary
        StringBuilder stats = new StringBuilder();
        if (sub.totalTestCases() != null && sub.totalTestCases() > 0) {
            stats.append(sub.passTestCases() != null ? sub.passTestCases() : 0)
                    .append("/").append(sub.totalTestCases()).append(" passed");
        }
        if (sub.executionTimeMs() != null) {
            if (!stats.isEmpty()) stats.append(" · ");
            stats.append(sub.executionTimeMs()).append("ms");
        }
        if (sub.score() != null && !isRun) {
            if (!stats.isEmpty()) stats.append(" · ");
            stats.append(String.format("Score: %.0f/100", sub.score()));
        }
        if (isRun) {
            stats.append(" (Public tests only)");
        }
        resultStatsLabel.setText(stats.toString());

        // Error message or output
        if (sub.errorMessage() != null && !sub.errorMessage().isBlank()) {
            resultDetailsArea.setText(sub.errorMessage());
            resultDetailsScrollPane.setVisible(true);
            toggleResultButton.setText("Hide Details");
        } else if (ac) {
            resultDetailsArea.setText("All test cases passed successfully!");
            resultDetailsScrollPane.setVisible(false);
            toggleResultButton.setText("Show Details");
        } else {
            resultDetailsArea.setText("No additional error details available.");
            resultDetailsScrollPane.setVisible(false);
            toggleResultButton.setText("Show Details");
        }

        revalidate();
        repaint();
    }

    private void showError(String message) {
        resultSection.setVisible(true);
        resultVerdictLabel.setText("ERROR");
        resultVerdictLabel.setForeground(new JBColor(new Color(215, 58, 73), new Color(248, 81, 73)));
        resultStatsLabel.setText("");
        resultDetailsArea.setText(message);
        resultDetailsScrollPane.setVisible(true);
        toggleResultButton.setText("Hide Details");
        revalidate();
        repaint();
    }

    private void executeNextStage() {
        Optional<Path> mlcRootOpt = CheckAction.findMlcRoot(project, null);
        if (mlcRootOpt.isEmpty()) {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "Could not locate project root with .mlc/project.yaml.",
                    NotificationType.WARNING);
            return;
        }

        NextStageAction action = new NextStageAction();
        action.executeSync(project, mlcRootOpt.get());

        // Refresh tree and current stage
        if (onStagePassedCallback != null) {
            onStagePassedCallback.run();
        }
    }
}
