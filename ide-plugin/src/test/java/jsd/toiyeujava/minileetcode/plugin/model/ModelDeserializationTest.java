package jsd.toiyeujava.minileetcode.plugin.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelDeserializationTest {

    private final Gson gson = new Gson();

    @Test
    @DisplayName("testDeserializePlanSummaryList: verify list and progress calculations")
    void testDeserializePlanSummaryList() {
        String json = """
            [
              {
                "id": 1,
                "slug": "sp-oop",
                "title": "OOP với Java",
                "description": "Lộ trình hướng đối tượng qua 5 stage Mini Bank",
                "status": "PUBLISHED",
                "itemCount": 2,
                "stageCount": 5,
                "doneStages": 2
              },
              {
                "id": 2,
                "slug": "sp-dsa",
                "title": "DSA Cơ Bản",
                "status": "DRAFT",
                "itemCount": 1,
                "stageCount": 0,
                "doneStages": 0
              }
            ]
            """;

        Type type = new TypeToken<List<PlanSummary>>() {}.getType();
        List<PlanSummary> list = gson.fromJson(json, type);

        assertNotNull(list);
        assertEquals(2, list.size());

        PlanSummary p1 = list.get(0);
        assertEquals(1L, p1.getId());
        assertEquals("sp-oop", p1.getSlug());
        assertEquals("OOP với Java", p1.getTitle());
        assertEquals("Lộ trình hướng đối tượng qua 5 stage Mini Bank", p1.getDescription());
        assertEquals(StudyPlanStatus.PUBLISHED, p1.getStatus());
        assertTrue(p1.getStatus().isPublished());
        assertEquals(2, p1.getItemCount());
        assertEquals(5, p1.getStageCount());
        assertEquals(2, p1.getDoneStages());
        assertEquals(40, p1.getProgressPercentage());
        assertEquals(40, p1.progressPercent());
        assertFalse(p1.isCompleted());

        PlanSummary p2 = list.get(1);
        assertEquals(2L, p2.getId());
        assertEquals(StudyPlanStatus.DRAFT, p2.getStatus());
        assertFalse(p2.getStatus().isPublished());
        assertEquals(0, p2.getProgressPercentage());
        assertFalse(p2.isCompleted());
    }

    @Test
    @DisplayName("testDeserializePlanDetailHierarchy: verify 3-level tree integrity and enum mapping")
    void testDeserializePlanDetailHierarchy() {
        String json = """
            {
              "id": 1,
              "slug": "sp-oop",
              "title": "OOP với Java",
              "description": "Lộ trình",
              "status": "PUBLISHED",
              "stageCount": 3,
              "doneStages": 3,
              "items": [
                {
                  "id": 10,
                  "position": 1,
                  "kind": "PROJECT",
                  "title": "Mini Bank Console",
                  "description": "Project ngân hàng",
                  "done": true,
                  "stages": [
                    {
                      "stageNo": 1,
                      "problemId": 101,
                      "slug": "mini-bank-stage-1",
                      "title": "Account & Main",
                      "difficulty": "EASY",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "DONE"
                    },
                    {
                      "stageNo": 2,
                      "problemId": 102,
                      "slug": "mini-bank-stage-2",
                      "title": "Abstract Account",
                      "difficulty": "MEDIUM",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "DONE"
                    },
                    {
                      "stageNo": 3,
                      "problemId": 103,
                      "slug": "mini-bank-stage-3",
                      "title": "InsufficientFundsException",
                      "difficulty": "HARD",
                      "problemType": "ALGORITHMIC",
                      "published": false,
                      "status": "LOCKED"
                    }
                  ]
                }
              ]
            }
            """;

        PlanDetail detail = gson.fromJson(json, PlanDetail.class);

        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("sp-oop", detail.getSlug());
        assertEquals("OOP với Java", detail.getTitle());
        assertEquals(100, detail.getProgressPercentage());
        assertTrue(detail.isCompleted());
        assertEquals(1, detail.getItemCount());
        assertEquals(1, detail.getItems().size());

        ItemDto item = detail.getItems().get(0);
        assertEquals(10L, item.getId());
        assertEquals(1, item.getPosition());
        assertEquals(StudyPlanItemKind.PROJECT, item.getKind());
        assertTrue(item.isProject());
        assertFalse(item.isProblem());
        assertTrue(item.isDone());
        assertEquals(3, item.getStageCount());
        assertEquals(2, item.getDoneStageCount());

        StageDto s1 = item.getStages().get(0);
        assertEquals(1, s1.getStageNo());
        assertEquals(101L, s1.getProblemId());
        assertEquals("mini-bank-stage-1", s1.getSlug());
        assertEquals("Account & Main", s1.getTitle());
        assertEquals(Difficulty.EASY, s1.getDifficulty());
        assertEquals(ProblemType.OOP_GRINDING, s1.getProblemType());
        assertTrue(s1.isPublished());
        assertEquals(StageStatus.DONE, s1.getStatus());
        assertTrue(s1.isDone());
        assertFalse(s1.isLocked());
        assertTrue(s1.canAttempt());

        StageDto s3 = item.getStages().get(2);
        assertEquals(Difficulty.HARD, s3.getDifficulty());
        assertEquals(ProblemType.ALGORITHMIC, s3.getProblemType());
        assertFalse(s3.isPublished());
        assertEquals(StageStatus.LOCKED, s3.getStatus());
        assertTrue(s3.isLocked());
        assertFalse(s3.canAttempt());
    }

    @Test
    @DisplayName("testDefensiveDefaultsOnNull: null values are safely handled by records")
    void testDefensiveDefaultsOnNull() {
        PlanDetail emptyDetail = new PlanDetail(null, null, null, null, null, 0, 0, null);
        assertEquals("", emptyDetail.slug());
        assertEquals("", emptyDetail.title());
        assertEquals("", emptyDetail.description());
        assertNotNull(emptyDetail.items());
        assertTrue(emptyDetail.items().isEmpty());
        assertEquals(0, emptyDetail.getItemCount());

        ItemDto emptyItem = new ItemDto(null, 0, null, null, null, false, null);
        assertEquals(StudyPlanItemKind.PROBLEM, emptyItem.kind());
        assertEquals("", emptyItem.title());
        assertEquals("", emptyItem.description());
        assertNotNull(emptyItem.stages());
        assertTrue(emptyItem.stages().isEmpty());

        StageDto emptyStage = new StageDto(0, null, null, null, null, null, false, null);
        assertEquals("", emptyStage.slug());
        assertEquals("", emptyStage.title());
        assertEquals(Difficulty.EASY, emptyStage.difficulty());
        assertEquals(ProblemType.OOP_GRINDING, emptyStage.problemType());
        assertEquals(StageStatus.LOCKED, emptyStage.status());
    }

    @Test
    @DisplayName("testDeserializeProblemDto: verify full JSON mapping and null defenses")
    void testDeserializeProblemDto() {
        String json = """
            {
              "id": 101,
              "title": "Account & Main",
              "slug": "mini-bank-stage-1",
              "description": "# Mini Bank - Stage 1\\n\\nCreate Account class.",
              "difficulty": "EASY",
              "timeLimitMs": 2000,
              "memoryLimitMb": 256,
              "status": "PUBLISHED",
              "createdById": 1,
              "createdByUsername": "instructor1",
              "createdAt": "2026-11-01T10:00:00",
              "updatedAt": "2026-11-02T12:00:00",
              "tags": ["OOP", "Java"],
              "testCaseCount": 5,
              "progress": "IN_PROGRESS",
              "starterTemplates": "[{\\"name\\":\\"Solution.java\\"}]",
              "type": "OOP_GRINDING"
            }
            """;

        ProblemDto dto = gson.fromJson(json, ProblemDto.class);

        assertNotNull(dto);
        assertEquals(101L, dto.getId());
        assertEquals("mini-bank-stage-1", dto.getSlug());
        assertEquals("Account & Main", dto.getTitle());
        assertEquals("# Mini Bank - Stage 1\n\nCreate Account class.", dto.getDescription());
        assertEquals(Difficulty.EASY, dto.getDifficulty());
        assertEquals(ProblemType.OOP_GRINDING, dto.getType());
        assertEquals(ProblemType.OOP_GRINDING, dto.getProblemType());
        assertTrue(dto.isOopGrinding());
        assertFalse(dto.isAlgorithmic());
        assertEquals(2000, dto.getTimeLimitMs());
        assertEquals(256, dto.getMemoryLimitMb());
        assertEquals(2, dto.getTags().size());
        assertTrue(dto.hasTags());
        assertTrue(dto.hasStarterTemplates());
        assertTrue(dto.isPublished());
        assertTrue(dto.isInProgress());
        assertFalse(dto.isDone());
        assertEquals(5L, dto.getTestCaseCount());
    }

    @Test
    @DisplayName("testProblemDtoNullSafetyAndAlternateField: verify defaults on missing/null fields and problemType alias")
    void testProblemDtoNullSafetyAndAlternateField() {
        String json = """
            {
              "id": 102,
              "problemType": "ALGORITHMIC",
              "tags": null
            }
            """;

        ProblemDto dto = gson.fromJson(json, ProblemDto.class);

        assertNotNull(dto);
        assertEquals(102L, dto.getId());
        assertEquals("", dto.getSlug());
        assertEquals("", dto.getTitle());
        assertEquals("", dto.getDescription());
        assertEquals(Difficulty.EASY, dto.getDifficulty());
        assertEquals(ProblemType.ALGORITHMIC, dto.getType());
        assertTrue(dto.isAlgorithmic());
        assertNotNull(dto.getTags());
        assertTrue(dto.getTags().isEmpty());
        assertEquals(2000, dto.effectiveTimeLimitMs());
        assertEquals(256, dto.effectiveMemoryLimitMb());
    }
}
