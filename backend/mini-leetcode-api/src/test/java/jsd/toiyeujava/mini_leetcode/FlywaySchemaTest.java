package jsd.toiyeujava.mini_leetcode;

import jakarta.persistence.EntityManager;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywaySchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Verification Gate 1: Flyway migration should successfully create all core tables on H2 test profile")
    void flywayMigration_createsAllCoreTables() throws Exception {
        Set<String> tables = new HashSet<>();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        assertThat(tables)
                .contains(
                        "users",
                        "problems",
                        "test_cases",
                        "contests",
                        "contest_participants",
                        "submissions",
                        "refresh_tokens"
                );
    }

    @Test
    @DisplayName("Verification Gate 2: V4 migration should add problem_type and submission scoring columns")
    void flywayMigration_v4_addsOopGrindingColumns() throws Exception {
        Set<String> problemColumns = new HashSet<>();
        Set<String> submissionColumns = new HashSet<>();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME").toLowerCase();
                    String colName = rs.getString("COLUMN_NAME").toLowerCase();
                    if ("problems".equals(tableName)) {
                        problemColumns.add(colName);
                    } else if ("submissions".equals(tableName)) {
                        submissionColumns.add(colName);
                    }
                }
            }
        }

        assertThat(problemColumns).contains("problem_type");
        assertThat(submissionColumns).contains("score", "execution_phase", "score_breakdown");
    }

    @Test
    @Transactional
    @DisplayName("Verification Gate 3: Raw SQL insert into problems omitting problem_type defaults cleanly to 'ALGORITHMIC'")
    void rawSqlInsert_omittingProblemType_defaultsToAlgorithmic() {
        User creator = getOrCreateTestUser("test_creator_gate3");

        // Simulate existing seed data (seed-problems.sql) omitting problem_type
        jdbcTemplate.update(
                "INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "Seed Problem Test", "seed-problem-gate3", "Description test", "EASY", 1000, 256, "PUBLISHED", creator.getId()
        );

        String problemType = jdbcTemplate.queryForObject(
                "SELECT problem_type FROM problems WHERE slug = ?",
                String.class,
                "seed-problem-gate3"
        );

        assertThat(problemType).isEqualTo("ALGORITHMIC");
    }

    @Test
    @Transactional
    @DisplayName("Verification Gate 4: Raw SQL insert into submissions omitting score fields allows NULL defaults, and accepts explicit values")
    void rawSqlInsert_submissionScoringColumns_handlesNullsAndExplicitValues() {
        User creator = getOrCreateTestUser("test_user_gate4");
        Problem problem = problemRepository.save(Problem.builder()
                .title("Gate 4 Problem")
                .slug("gate4-problem")
                .description("Gate 4 Desc")
                .difficulty(Difficulty.MEDIUM)
                .createdBy(creator)
                .build());

        // 1. Insert omitting score, execution_phase, score_breakdown
        jdbcTemplate.update(
                "INSERT INTO submissions (user_id, problem_id, language, code_storage_uri, status, pass_test_cases, total_test_cases) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                creator.getId(), problem.getId(), "JAVA", "minio://submissions/1.java", "PENDING", 0, 0
        );

        Long sub1Id = jdbcTemplate.queryForObject(
                "SELECT id FROM submissions WHERE problem_id = ? AND status = 'PENDING'",
                Long.class,
                problem.getId()
        );
        assertThat(sub1Id).isNotNull();

        Double score1 = jdbcTemplate.queryForObject("SELECT score FROM submissions WHERE id = ?", Double.class, sub1Id);
        String phase1 = jdbcTemplate.queryForObject("SELECT execution_phase FROM submissions WHERE id = ?", String.class, sub1Id);
        String breakdown1 = jdbcTemplate.queryForObject("SELECT score_breakdown FROM submissions WHERE id = ?", String.class, sub1Id);

        assertThat(score1).isNull();
        assertThat(phase1).isNull();
        assertThat(breakdown1).isNull();

        // 2. Insert with explicit score, execution_phase, and valid JSON score_breakdown
        String jsonBreakdown = "{\"totalScore\":85.5,\"compilationPassed\":true}";
        jdbcTemplate.update(
                "INSERT INTO submissions (user_id, problem_id, language, code_storage_uri, status, pass_test_cases, total_test_cases, score, execution_phase, score_breakdown) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                creator.getId(), problem.getId(), "JAVA", "minio://submissions/2.java", "COMPLETED", 5, 5, 85.5, "CONTAINER_RUN", jsonBreakdown
        );

        Double score2 = jdbcTemplate.queryForObject("SELECT score FROM submissions WHERE status = 'COMPLETED'", Double.class);
        String phase2 = jdbcTemplate.queryForObject("SELECT execution_phase FROM submissions WHERE status = 'COMPLETED'", String.class);
        String breakdown2 = jdbcTemplate.queryForObject("SELECT score_breakdown FROM submissions WHERE status = 'COMPLETED'", String.class);

        assertThat(score2).isEqualTo(85.5);
        assertThat(phase2).isEqualTo("CONTAINER_RUN");
        assertThat(breakdown2).contains("85.5");
    }

    @Test
    @Transactional
    @DisplayName("Verification Gate 5: JPA Entity mapping for Problem supports ALGORITHMIC and OOP_GRINDING types")
    void jpaEntityMapping_problemTypePersistsAndLoadsCorrectly() {
        User creator = getOrCreateTestUser("test_user_gate5");

        // Default type should be ALGORITHMIC
        Problem p1 = Problem.builder()
                .title("Algorithmic Problem")
                .slug("algo-problem-gate5")
                .description("Algo Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(creator)
                .build();
        Problem savedP1 = problemRepository.save(p1);

        // Explicit OOP_GRINDING type
        Problem p2 = Problem.builder()
                .title("OOP Grinding Problem")
                .slug("oop-problem-gate5")
                .description("OOP Desc")
                .difficulty(Difficulty.HARD)
                .problemType(ProblemType.OOP_GRINDING)
                .createdBy(creator)
                .build();
        Problem savedP2 = problemRepository.save(p2);

        entityManager.flush();
        entityManager.clear();

        Problem loadedP1 = problemRepository.findById(savedP1.getId()).orElseThrow();
        assertThat(loadedP1.getProblemType()).isEqualTo(ProblemType.ALGORITHMIC);

        Problem loadedP2 = problemRepository.findById(savedP2.getId()).orElseThrow();
        assertThat(loadedP2.getProblemType()).isEqualTo(ProblemType.OOP_GRINDING);
    }

    @Test
    @Transactional
    @DisplayName("Verification Gate 6: JPA Entity mapping for Submission with @JdbcTypeCode(SqlTypes.JSON) persists and retrieves 5KB+ complex JSON")
    void jpaEntityMapping_submissionJsonPersistsAndRetrieves5KbPayload() {
        User creator = getOrCreateTestUser("test_user_gate6");
        Problem problem = problemRepository.save(Problem.builder()
                .title("OOP Grinding Submission Problem")
                .slug("oop-sub-problem-gate6")
                .description("OOP Sub Desc")
                .difficulty(Difficulty.MEDIUM)
                .problemType(ProblemType.OOP_GRINDING)
                .createdBy(creator)
                .build());

        // Construct 5KB+ nested JSON payload with Vietnamese Unicode, quotes, and deep structures
        StringBuilder sb = new StringBuilder();
        sb.append("{\"meta\":{\"version\":\"1.0\",\"evaluator\":\"OopGrindingEvaluator\"},");
        sb.append("\"rubric\":[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":%d,\"criterion\":\"OOP_Principle_%d\",\"weight\":2.5,\"score\":2.5,\"passed\":true,\"feedback\":\"Tiêu chí %d: Thực thi thành công, không phát hiện vi phạm SOLID (Liskov Substitution Principle).\"}",
                    i, i, i
            ));
        }
        sb.append("],\"largeBuffer\":\"").append("X".repeat(2500)).append("\"}");
        String complexJson = sb.toString();

        assertThat(complexJson.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(5000);

        Submission submission = Submission.builder()
                .user(creator)
                .problem(problem)
                .language(SubmissionLanguage.JAVA)
                .codeStorageUri("minio://submissions/complex.java")
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.AC)
                .score(100.0)
                .executionPhase("CONTAINER_RUN")
                .scoreBreakdown(complexJson)
                .build();

        Submission saved = submissionRepository.save(submission);

        entityManager.flush();
        entityManager.clear();

        Submission loaded = submissionRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getScore()).isEqualTo(100.0);
        assertThat(loaded.getExecutionPhase()).isEqualTo("CONTAINER_RUN");
        assertThat(loaded.getScoreBreakdown()).isEqualTo(complexJson);
    }

    @Test
    @DisplayName("Verification Gate 7: Entire seed-problems.sql file executes twice on real MySQL without error")
    void seedProblemsSql_executesSuccessfullyWithoutError() throws Exception {
        // Seed được sinh bởi backend/tools/problems/seed_sql.py và không commit; chạy từ module api hoặc từ backend.
        Path scriptPath = Path.of("..", "tools", "seed-problems.sql");
        if (!Files.exists(scriptPath)) {
            scriptPath = Path.of("tools", "seed-problems.sql");
        }
        Assumptions.assumeTrue(Files.exists(scriptPath),
                "backend/tools/seed-problems.sql not generated; run: python backend/tools/problems/seed_sql.py");
        // Seed dùng cú pháp MySQL (SET @var, ON DUPLICATE KEY, TEMPORARY TABLE) nên phải chạy trên MySQL thật, không phải H2.
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available; seed-problems.sql needs a real MySQL container, test skipped");

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            DataSource dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            JdbcTemplate mysqlJdbc = new JdbcTemplate(dataSource);
            String countSql = "SELECT COUNT(*) FROM problems WHERE problem_type = 'ALGORITHMIC'";
            Integer countAfterRun = null;
            for (int run = 1; run <= 2; run++) {
                // Một connection cho cả script vì seed dùng biến phiên và bảng TEMPORARY.
                try (Connection connection = dataSource.getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new EncodedResource(new PathResource(scriptPath), StandardCharsets.UTF_8));
                }
                Integer count = mysqlJdbc.queryForObject(countSql, Integer.class);
                assertThat(count).isGreaterThanOrEqualTo(10);
                if (countAfterRun != null) {
                    assertThat(count).as("second run must not duplicate problems").isEqualTo(countAfterRun);
                }
                countAfterRun = count;
            }
        }
    }

    private User getOrCreateTestUser(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .email(username + "@test.com")
                        .passwordHash("hashed_password_for_tests")
                        .role(Role.USER)
                        .status(UserStatus.ACTIVE)
                        .build()));
    }
}
