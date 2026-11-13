package jsd.toiyeujava.mini_leetcode.topic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;
import jsd.toiyeujava.mini_leetcode.topic.service.StepGrader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chấm và kiểm tra nội dung từng loại step (specs/topics.spec.md). */
class StepGraderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StepGrader grader = new StepGrader(mapper);

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s.replace('\'', '"'));
    }

    // ── CHOICE ──────────────────────────────────────────────────────────────

    private static final String CHOICE = "{'prompt':'Field nào nên private?','multiple':true,"
            + "'options':[{'id':'a','text':'balance'},{'id':'b','text':'owner'},{'id':'c','text':'MAX (hằng số)'}]}";

    @Test
    void choiceNeedsTheExactSetOfCorrectOptions() throws Exception {
        JsonNode answer = json("{'correct':['a','b'],'explanation':'Trạng thái luôn private.'}");
        StepGrader.Grade partial = grader.grade(TopicStepType.CHOICE, json(CHOICE), answer, json("{'selected':['a']}"));
        assertThat(partial.correct()).isFalse();
        assertThat(partial.message()).contains("2 correct options");
        assertThat(partial.explanation()).isNull();

        StepGrader.Grade ok = grader.grade(TopicStepType.CHOICE, json(CHOICE), answer, json("{'selected':['b','a']}"));
        assertThat(ok.correct()).isTrue();
        assertThat(ok.explanation()).isEqualTo("Trạng thái luôn private.");
    }

    @Test
    void singleChoiceRejectsSeveralCorrectOptionsAndUnknownIds() throws Exception {
        JsonNode single = json("{'prompt':'p','options':[{'id':'a','text':'x'},{'id':'b','text':'y'}]}");
        assertThatThrownBy(() -> grader.validate(TopicStepType.CHOICE, single, json("{'correct':['a','b']}"), "steps[0]"))
                .hasMessageContaining("exactly one correct option");
        assertThatThrownBy(() -> grader.validate(TopicStepType.CHOICE, single, json("{'correct':['z']}"), "steps[0]"))
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> grader.validate(TopicStepType.CHOICE, json("{'prompt':'p','options':[{'id':'a','text':'x'}]}"),
                json("{'correct':['a']}"), "steps[2]")).hasMessageStartingWith("steps[2]").hasMessageContaining("2 to 10");
    }

    // ── MATCHING ────────────────────────────────────────────────────────────

    private static final String MATCHING = "{'prompt':'Ghép modifier với phạm vi',"
            + "'left':[{'id':'l1','text':'private'},{'id':'l2','text':'public'},{'id':'l3','text':'protected'}],"
            + "'right':[{'id':'r1','text':'Chỉ trong lớp'},{'id':'r2','text':'Mọi nơi'},{'id':'r3','text':'Package + lớp con'}]}";
    private static final String MATCHING_ANSWER = "{'pairs':{'l1':'r1','l2':'r2','l3':'r3'}}";

    @Test
    void matchingCountsCorrectPairsWithoutRevealingThem() throws Exception {
        StepGrader.Grade wrong = grader.grade(TopicStepType.MATCHING, json(MATCHING), json(MATCHING_ANSWER),
                json("{'pairs':{'l1':'r1','l2':'r3','l3':'r2'}}"));
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.message()).isEqualTo("1 of 3 pairs are correct.");
        assertThat(grader.grade(TopicStepType.MATCHING, json(MATCHING), json(MATCHING_ANSWER), json(MATCHING_ANSWER)).correct())
                .isTrue();
    }

    @Test
    void matchingAnswerMustCoverEveryLeftItemOnce() throws Exception {
        assertThatThrownBy(() -> grader.validate(TopicStepType.MATCHING, json(MATCHING),
                json("{'pairs':{'l1':'r1','l2':'r1','l3':'r3'}}"), "s")).hasMessageContaining("matched twice");
        assertThatThrownBy(() -> grader.validate(TopicStepType.MATCHING, json(MATCHING),
                json("{'pairs':{'l1':'r1','l2':'r2'}}"), "s")).hasMessageContaining("'l3'");
    }

    // ── ORDERING ────────────────────────────────────────────────────────────

    private static final String ORDERING = "{'prompt':'Sắp xếp constructor','items':["
            + "{'id':'a','text':'public Account(String owner) {'},{'id':'b','text':'super();'},"
            + "{'id':'c','text':'this.owner = owner;'},{'id':'d','text':'}'}]}";
    private static final String ORDER = "{'order':['a','b','c','d']}";

    @Test
    void orderingCountsLinesInPlaceAndPublicOrderNeverEqualsTheAnswer() throws Exception {
        assertThat(grader.grade(TopicStepType.ORDERING, json(ORDERING), json(ORDER), json("{'order':['a','c','b','d']}"))
                .message()).isEqualTo("2 of 4 lines are in the right place.");
        assertThat(grader.grade(TopicStepType.ORDERING, json(ORDERING), json(ORDER), json(ORDER)).correct()).isTrue();

        for (long seed = 1; seed <= 50; seed++) {
            JsonNode pub = grader.publicContent(TopicStepType.ORDERING, json(ORDERING), json(ORDER), seed);
            List<String> ids = new ArrayList<>();
            pub.get("items").forEach(i -> ids.add(i.get("id").asText()));
            assertThat(ids).containsExactlyInAnyOrder("a", "b", "c", "d").isNotEqualTo(List.of("a", "b", "c", "d"));
        }
        // Ổn định: cùng step thì cùng thứ tự
        assertThat(grader.publicContent(TopicStepType.ORDERING, json(ORDERING), json(ORDER), 9))
                .isEqualTo(grader.publicContent(TopicStepType.ORDERING, json(ORDERING), json(ORDER), 9));
    }

    // ── FILL_BLANK ──────────────────────────────────────────────────────────

    private static final String FILL = "{'prompt':'Hoàn thành lớp trừu tượng',"
            + "'code':'public [[1]] class Shape {\\n    public abstract double [[2]]();\\n}'}";
    private static final String FILL_ANSWER = "{'blanks':{'1':['abstract'],'2':['area','getArea']},'explanation':'e'}";

    @Test
    void fillBlankNormalisesSpacesAndCaseAndNamesWrongBlanks() throws Exception {
        assertThat(grader.grade(TopicStepType.FILL_BLANK, json(FILL), json(FILL_ANSWER),
                json("{'values':{'1':'  Abstract ','2':'getArea'}}")).correct()).isTrue();
        StepGrader.Grade wrong = grader.grade(TopicStepType.FILL_BLANK, json(FILL), json(FILL_ANSWER),
                json("{'values':{'1':'final','2':'size'}}"));
        assertThat(wrong.message()).isEqualTo("Blanks 1 and 2 are not right yet.");
        assertThat(wrong.explanation()).isNull();
        assertThat(grader.grade(TopicStepType.FILL_BLANK, json(FILL),
                json("{'blanks':{'1':['abstract'],'2':['area']},'caseSensitive':true}"),
                json("{'values':{'1':'Abstract','2':'area'}}")).message()).isEqualTo("Blank 1 is not right yet.");
    }

    @Test
    void fillBlankNeedsAnAcceptedAnswerForEveryBlank() throws Exception {
        assertThatThrownBy(() -> grader.validate(TopicStepType.FILL_BLANK, json(FILL),
                json("{'blanks':{'1':['abstract']}}"), "s")).hasMessageContaining("blank '2'");
        assertThatThrownBy(() -> grader.validate(TopicStepType.FILL_BLANK, json("{'prompt':'p','code':'no blanks'}"),
                json("{'blanks':{}}"), "s")).hasMessageContaining("[[id]]");
        grader.validate(TopicStepType.FILL_BLANK, json(FILL), json(FILL_ANSWER), "s");
    }

    // ── Nội dung công khai ──────────────────────────────────────────────────

    @Test
    void publicContentNeverContainsTheAnswerKeyAndKeepsAllItems() throws Exception {
        JsonNode pub = grader.publicContent(TopicStepType.MATCHING, json(MATCHING), json(MATCHING_ANSWER), 3);
        assertThat(pub.has("pairs")).isFalse();
        assertThat(pub.get("right")).hasSize(3);
        assertThat(pub.get("left")).isEqualTo(json(MATCHING).get("left"));

        JsonNode fill = grader.publicContent(TopicStepType.FILL_BLANK, json(FILL), json(FILL_ANSWER), 1);
        assertThat(fill.toString()).doesNotContain("getArea");
        assertThat(fill.get("blanks")).hasSize(2);
    }

    @Test
    void theoryIsReadByAnySubmissionAndCodeCannotBeAttempted() throws Exception {
        assertThat(grader.grade(TopicStepType.THEORY, json("{'markdown':'## A'}"), null, json("{}")).correct()).isTrue();
        assertThatThrownBy(() -> grader.grade(TopicStepType.CODE, json("{}"), null, json("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grader.validate(TopicStepType.THEORY, json("{'markdown':' '}"), null, "s"))
                .hasMessageContaining("markdown");
    }
}
