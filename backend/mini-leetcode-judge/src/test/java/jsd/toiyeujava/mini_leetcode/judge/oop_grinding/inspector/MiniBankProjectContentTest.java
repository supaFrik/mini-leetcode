package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopInspectionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopRuleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nội dung project Mini Bank (backend/tools/problems/projects/mini-bank, spec study-plan slice 2):
 * rule OOP cộng dồn của mỗi stage phải chấp nhận lời giải tham chiếu và phải thật sự bắt lỗi.
 */
class MiniBankProjectContentTest {

    private static final Path TOOLS = Path.of("..", "tools", "problems");
    private static final Path PROJECT = TOOLS.resolve("projects").resolve("mini-bank");
    private static final Path REFERENCE = TOOLS.resolve("oop-starters").resolve("mini-bank-console");
    private final ObjectMapper mapper = new ObjectMapper();
    private final OopGrindingInspector inspector = new OopGrindingInspector(mapper);

    @ParameterizedTest(name = "stage {0}: reference solution passes every cumulative rule")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void referencePassesStageRules(int stage, @TempDir Path classes) throws Exception {
        compile(classes, javaFiles(REFERENCE));

        OopInspectionReport report = inspector.inspect(classes, rulesFor(stage));

        assertThat(report.getRules()).filteredOn(r -> !r.isPassed()).extracting(OopRuleResult::getRuleName).isEmpty();
        assertThat(report.getScore()).isEqualTo(100.0);
    }

    @Test
    void stage2RulesRejectTheConcreteStage1Account(@TempDir Path classes) throws Exception {
        compile(classes, javaFiles(PROJECT.resolve("starters").resolve("stage-1")));

        OopInspectionReport report = inspector.inspect(classes, rulesFor(2));

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getRules()).filteredOn(r -> !r.isPassed()).extracting(OopRuleResult::getRuleName)
                .contains("ACCOUNT_IS_ABSTRACT_BASE", "SAVINGS_EXTENDS_ACCOUNT", "CHECKING_EXTENDS_ACCOUNT");
    }

    @Test
    void everyStageHasStatementAndAPublicSampleCase() throws Exception {
        JsonNode project = mapper.readTree(PROJECT.resolve("project.json").toFile());
        for (int stage = 1; stage <= project.get("stages").size(); stage++) {
            assertThat(PROJECT.resolve("stages").resolve("stage-" + stage + ".md")).isRegularFile();
            int current = stage;
            assertThat(project.get("cases")).as("stage %d needs its own sample case", stage)
                    .anyMatch(c -> c.get("stage").asInt() == current);
        }
    }

    /** Giống mini_bank_project.py: rule có fromStage <= stage, bỏ trường fromStage. */
    private String rulesFor(int stage) throws IOException {
        ArrayNode picked = mapper.createArrayNode();
        for (JsonNode rule : mapper.readTree(PROJECT.resolve("grading.json").toFile()).get("rules")) {
            if (rule.get("fromStage").asInt() <= stage) {
                picked.add(((ObjectNode) rule.deepCopy()).without("fromStage"));
            }
        }
        return mapper.writeValueAsString(picked);
    }

    private static List<Path> javaFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static void compile(Path out, List<Path> sources) {
        List<String> args = new ArrayList<>(List.of("-encoding", "UTF-8", "-d", out.toString()));
        sources.forEach(p -> args.add(p.toString()));
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertThat(result).as("javac exit code").isZero();
    }
}
